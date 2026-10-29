package com.pydlove.workcove.development.domain.port;

import com.pydlove.workcove.development.domain.model.WorkFileMatch;

import java.util.List;

public interface WorkFileSearchGateway {

    List<WorkFileMatch> search(List<String> roots, String query);

    void openFile(String path, List<String> roots);
}
