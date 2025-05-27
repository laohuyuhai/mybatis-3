package org.apache.ibatis.myvalidation.mapper;

import org.apache.ibatis.myvalidation.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {
    /**
     * 查询用户信息
     */
    User getById(@Param("id") Long id);
}
