package com.aistudyassistant.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    private int chunkIndex;

    @Column(columnDefinition = "LONGTEXT")
    private String embedding; // JSON vector

    @ManyToOne
    @JoinColumn(name = "document_id")
    private Document document;
}