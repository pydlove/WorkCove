package com.pydlove.workcove.development.domain.port;

/** 调用宿主机原生目录选择器，并通过系统文件管理器打开目录。 */
public interface DirectoryPicker {

    String chooseDirectory();

    void openDirectory(String path);
}
