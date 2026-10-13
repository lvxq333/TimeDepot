-- 现有数据库执行一次；历史浏览器 localStorage 次数无法可靠回填，原有账号从迁移后获得一次机会。
ALTER TABLE `user`
    ADD COLUMN `nickname_changed_at` DATETIME NULL
    COMMENT '最近一次实际修改昵称的北京时间，注册昵称不计入'
    AFTER `nickname`;
