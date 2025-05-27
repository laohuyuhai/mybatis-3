create table user_mybatis.user (
    id varchar(20) primary key comment '主键',
    username varchar(30) not null comment '姓名'
) engine = Innodb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

insert into user_mybatis.user(id, username) values
('1', '张三'),
('2', '李四');