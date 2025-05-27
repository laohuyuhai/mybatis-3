/*
 *    Copyright 2009-2023 the original author or authors.
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
package org.apache.ibatis.session;

import org.apache.ibatis.exceptions.PersistenceException;

/**
 * @author Clinton Begin
 */
public class SqlSessionException extends PersistenceException {

  // serialVersionUID其实写多少都行，主要是为了序列化和反序列化
  // 如果你不写，Java就会默认生成一个, 默认生成的serialVersionUID是1L，但是一旦修改了类的字段，就会自动生成新的serialVersionUID，导致反序列化失败
  // 所以这个东西写啥都行，但是不要乱改，除非你想让反序列化跪掉
  private static final long serialVersionUID = 3833184690240265047L;

  public SqlSessionException() {
  }

  public SqlSessionException(String message) {
    super(message);
  }

  public SqlSessionException(String message, Throwable cause) {
    super(message, cause);
  }

  public SqlSessionException(Throwable cause) {
    super(cause);
  }
}
