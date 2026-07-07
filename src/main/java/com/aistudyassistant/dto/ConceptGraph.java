package com.aistudyassistant.dto;

import java.util.List;

public record ConceptGraph(
        List<Node> nodes,
        List<Edge> edges
) {

    public record Node(
            String id,
            String label
    ) {}

    public record Edge(
            String from,
            String to
    ) {}

}