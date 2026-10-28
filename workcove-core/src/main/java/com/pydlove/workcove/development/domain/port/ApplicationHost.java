package com.pydlove.workcove.development.domain.port;

import java.util.List;
import java.util.Set;

/** 受限的本机应用选择、启动和退出适配器。 */
public interface ApplicationHost {

    SelectedApplication chooseApplication();

    void openApplication(String path);

    Set<String> runningApplicationPaths(List<String> paths);

    void forceQuitApplication(String path);

    record SelectedApplication(
            String path,
            String name,
            String iconDataUrl
    ) {
    }
}
