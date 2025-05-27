package org.apache.ibatis.mytest;

import org.apache.ibatis.myvalidation.entity.User;
import org.apache.ibatis.myvalidation.mapper.UserMapper;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

public class MybatisDemoApplicationTests {
    public static void main(String[] args) {
        //1、读取配置文件
        String resource = "myres/mybatis-config.xml";
        InputStream inputStream;
        SqlSession sqlSession = null;
        try {
            inputStream = Resources.getResourceAsStream(resource);
            //2、初始化mybatis，创建SqlSessionFactory类实例
            SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
            //3、创建Session实例
            sqlSession = sqlSessionFactory.openSession();
            //4、获取Mapper接口
            UserMapper userMapper = sqlSession.getMapper(UserMapper.class);
            //5、执行SQL操作
            User user = userMapper.getById(1L);
            System.out.println(user);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            //6、关闭sqlSession会话
            if (null != sqlSession) {
                sqlSession.close();
            }
        }
    }
}
