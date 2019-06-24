package org.wickedsource.coderadar.analyzer.levelizedStructureMap;

import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Service;

import java.util.LinkedList;

@Service
public class DependencyTreeService {

    public Node getDependencyTree(Repository repository, String commitName, String basepackage, String repoName) {
        DependencyTree dependencyTree = new DependencyTree(basepackage, commitName, repository);

        Node baseRoot = new Node(new LinkedList<>(), repository.getWorkTree().getParentFile().getPath(), repoName, "");
        dependencyTree.createTreeRoot(baseRoot);

        dependencyTree.setDependencies(baseRoot);
        baseRoot.setDependencies(new LinkedList<>());
        dependencyTree.sortTree(baseRoot);
        dependencyTree.setLayer(baseRoot);

        return baseRoot;
    }
}