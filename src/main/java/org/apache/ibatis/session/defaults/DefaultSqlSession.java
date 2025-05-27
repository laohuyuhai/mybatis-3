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
package org.apache.ibatis.session.defaults;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.result.DefaultMapResultHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * The default implementation for {@link SqlSession}. Note that this class is not Thread-Safe.
 *
 * @author Clinton Begin
 */
public class DefaultSqlSession implements SqlSession {

  private final Configuration configuration;
  private final Executor executor;

  private final boolean autoCommit;
  private boolean dirty;
  private List<Cursor<?>> cursorList;

  public DefaultSqlSession(Configuration configuration, Executor executor, boolean autoCommit) {
    this.configuration = configuration;
    this.executor = executor;
    this.dirty = false;
    this.autoCommit = autoCommit;
  }

  public DefaultSqlSession(Configuration configuration, Executor executor) {
    this(configuration, executor, false);
  }

  @Override
  public <T> T selectOne(String statement) {
    return this.selectOne(statement, null);
  }

  @Override
  public <T> T selectOne(String statement, Object parameter) {
    // Popular vote was to return null on 0 results and throw exception on too many.
    List<T> list = this.selectList(statement, parameter);
    // 哈哈，没想到开源代码也用这种复用的方式，不过他们要严谨一些，判断了列表中的元素数量，数量不对直接抛异常
    if (list.size() == 1) {
      return list.get(0);
    }
    if (list.size() > 1) {
      throw new TooManyResultsException(
          "Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
    } else {
      return null;
    }
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
    return this.selectMap(statement, null, mapKey, RowBounds.DEFAULT);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
    return this.selectMap(statement, parameter, mapKey, RowBounds.DEFAULT);
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    final List<? extends V> list = selectList(statement, parameter, rowBounds);
    final DefaultMapResultHandler<K, V> mapResultHandler = new DefaultMapResultHandler<>(mapKey,
        configuration.getObjectFactory(), configuration.getObjectWrapperFactory(), configuration.getReflectorFactory());
    final DefaultResultContext<V> context = new DefaultResultContext<>();
    for (V o : list) {
      context.nextResultObject(o);
      mapResultHandler.handleResult(context);
    }
    return mapResultHandler.getMappedResults();
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement) {
    return selectCursor(statement, null);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter) {
    return selectCursor(statement, parameter, RowBounds.DEFAULT);
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      dirty |= ms.isDirtySelect();
      Cursor<T> cursor = executor.queryCursor(ms, wrapCollection(parameter), rowBounds);
      registerCursor(cursor);
      return cursor;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public <E> List<E> selectList(String statement) {
    return this.selectList(statement, null);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter) {
    return this.selectList(statement, parameter, RowBounds.DEFAULT);
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    return selectList(statement, parameter, rowBounds, Executor.NO_RESULT_HANDLER);
  }

  // RowBounds是用来控制分页的，它的主要属性是limit和offset
  private <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      dirty |= ms.isDirtySelect();
      return executor.query(ms, wrapCollection(parameter), rowBounds, handler);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void select(String statement, Object parameter, ResultHandler handler) {
    select(statement, parameter, RowBounds.DEFAULT, handler);
  }

  @Override
  public void select(String statement, ResultHandler handler) {
    select(statement, null, RowBounds.DEFAULT, handler);
  }

  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    selectList(statement, parameter, rowBounds, handler);
  }

  @Override
  public int insert(String statement) {
    return insert(statement, null);
  }

  @Override
  public int insert(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public int update(String statement) {
    return update(statement, null);
  }

  @Override
  public int update(String statement, Object parameter) {
    try {
      dirty = true;
      MappedStatement ms = configuration.getMappedStatement(statement);
      return executor.update(ms, wrapCollection(parameter));
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public int delete(String statement) {
    return update(statement, null);
  }

  @Override
  public int delete(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public void commit() {
    commit(false);
  }

  @Override
  public void commit(boolean force) {
    try {
      executor.commit(isCommitOrRollbackRequired(force));
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error committing transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void rollback() {
    rollback(false);
  }

  @Override
  public void rollback(boolean force) {
    try {
      executor.rollback(isCommitOrRollbackRequired(force));
      dirty = false;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error rolling back transaction.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public List<BatchResult> flushStatements() {
    try {
      return executor.flushStatements();
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error flushing statements.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public void close() {
    try {
      executor.close(isCommitOrRollbackRequired(false));
      closeCursors();
      dirty = false;
    } finally {
      ErrorContext.instance().reset();
    }
  }

  private void closeCursors() {
    if (cursorList != null && !cursorList.isEmpty()) {
      for (Cursor<?> cursor : cursorList) {
        try {
          cursor.close();
        } catch (IOException e) {
          throw ExceptionFactory.wrapException("Error closing cursor.  Cause: " + e, e);
        }
      }
      cursorList.clear();
    }
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  @Override
  public <T> T getMapper(Class<T> type) {
    // getMapper 不直接实例化接口，而是通过动态代理生成接口的实现类。调用接口方法时，实际由 SqlSession 执行对应的 SQL
    return configuration.getMapper(type, this);
  }

  @Override
  public Connection getConnection() {
    try {
      // MyBatis 默认使用 autoCommit = false（手动提交），即通过 JdbcTransaction 获取连接时设置为非自动提交。
      //  第一次调用 getConnection() 时会触发 openConnection()，并根据配置设置事务隔离级别和 autoCommit。
      //  只要不调用 commit() 或 rollback()，事务就会一直存在，直到连接关闭或超时。
      //  autoCommit=on, 每条 SQL 语句被视为一个独立事务，执行完后立即提交。不会显式开启事务

      // 不是所有的连接一开始就有事务，但：
      //  只要执行了 DML 语句且 autocommit=off，就会自动创建事务。
      //  MyBatis 默认使用手动提交模式，因此每次获取连接后执行 SQL 通常都会进入事务状态。
      return executor.getTransaction().getConnection();
    } catch (SQLException e) {
      throw ExceptionFactory.wrapException("Error getting a new connection.  Cause: " + e, e);
    }
  }

  @Override
  public void clearCache() {
    executor.clearLocalCache();
  }

  private <T> void registerCursor(Cursor<T> cursor) {
    if (cursorList == null) {
      cursorList = new ArrayList<>();
    }
    cursorList.add(cursor);
  }

  // 这个方法是 MyBatis 控制事务行为的关键逻辑之一。它确保：
  //  在手动提交模式下，只有真正发生过数据更改（insert/update/delete）才提交；
  //  提供强制提交/回滚的能力，用于特殊场景；
  //  避免在自动提交模式下不必要的 commit/rollback 调用。
  private boolean isCommitOrRollbackRequired(boolean force) {
    return !autoCommit && dirty || force;
  }

  private Object wrapCollection(final Object object) {
    return ParamNameResolver.wrapToMapIfCollection(object, null);
  }

  /**
   * @deprecated Since 3.5.5
   */
  @Deprecated
  public static class StrictMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -5741767162221585340L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + this.keySet());
      }
      return super.get(key);
    }

  }

}
