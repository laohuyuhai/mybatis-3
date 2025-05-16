/*
 *    Copyright 2009-2024 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * <p>
 * Simple blocking decorator
 * <p>
 * Simple and inefficient version of EhCache's BlockingCache decorator. It sets a lock over a cache key when the element
 * is not found in cache. This way, other threads will wait until this element is filled instead of hitting the
 * database.
 * <p>
 * By its nature, this implementation can cause deadlock when used incorrectly.
 *
 * @author Eduardo Macarron
 */
public class BlockingCache implements Cache {

  private long timeout;
  // 装饰器模式
  private final Cache delegate;
  // 保证多线程操作的线程安全性
  private final ConcurrentHashMap<Object, CountDownLatch> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      // 为什么这里没加锁，却要释放锁
      // 1. 锁的获取由 getObject 完成
      //    只有在缓存未命中（即 getObject 找不到值）时，线程才会去获取锁。
      //    获取锁之后，该线程会负责从数据库加载数据并调用 putObject。
      //    因此，只有真正执行填充缓存操作的线程才会走到 putObject，而它已经持有锁。
      // 2. 释放锁是为了唤醒等待线程
      //    当前线程在完成 putObject 后调用 releaseLock，其核心目的是：
      //    告诉其他正在等待这个 key 的线程：“我已经填好数据了，你们可以继续争抢锁并读取数据了。”
      //    通过 latch.countDown() 来唤醒所有阻塞在 await() 上的线程。
      // 3. 为何不在 putObject 中获取锁？
      //    如果在 putObject 中也获取锁，会导致重复加锁、死锁或资源浪费。
      //    锁机制的核心是“谁查数据库谁加锁”，所以锁应该由 getObject 获取，而不是 putObject。
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    acquireLock(key);
    // 只要没抛出异常，就说明获取锁成功
    Object value = delegate.getObject(key);
    // 如果value是null, 说明缓存中没有这个key对应的value，则需要去数据库中查询,这个时候锁是没有释放的
    // 最终的释放操作是在putObject方法中
    if (value != null) {
      releaseLock(key);
    }
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    // despite its name, this method is called only to release locks
    releaseLock(key);
    return null;
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  // 正常返回则代表获取到了锁，否则就会抛出异常或一直等待
  private void acquireLock(Object key) {
    CountDownLatch newLatch = new CountDownLatch(1);
    while (true) {
      CountDownLatch latch = locks.putIfAbsent(key, newLatch);
      // latch为null，说明之前没有对key加锁，则本次获取锁成功，直接退出即可
      if (latch == null) {
        // 获取锁成功的唯一出口
        break;
      }
      // 否则，进入等待获取锁的步骤
      try {
        if (timeout > 0) {
          // 如果被成功唤醒，则进入循环，尝试重新获取锁
          boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);
          if (!acquired) {
            throw new CacheException(
                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
          }
        } else { // 如果没有设置超时时间，就会一直等待，直到被唤醒或者被打断，被唤醒后进入循环，尝试重新获取锁
          latch.await();
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    }
  }

  private void releaseLock(Object key) {
    CountDownLatch latch = locks.remove(key);
    if (latch == null) {
      throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
    }
    // 前面remove之后，不就把锁给释放了吗，那为什么这里还要countDown呢
    // 其实这是为了通知那些处在await状态的线程，告诉他们我释放锁了，你们可以去争抢了
    latch.countDown();
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}
