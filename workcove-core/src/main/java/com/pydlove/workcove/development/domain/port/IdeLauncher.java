package com.pydlove.workcove.development.domain.port;

/** 使用宿主机已安装的 IntelliJ IDEA 打开项目目录。 */
public interface IdeLauncher {

    void openProject(String path);
}
