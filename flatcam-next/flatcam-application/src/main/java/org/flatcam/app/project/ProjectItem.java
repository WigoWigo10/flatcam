package org.flatcam.app.project;

import java.util.Objects;
import java.util.UUID;

/**
 * A minimal, immutable stand-in for a project object (Gerber/Excellon/
 * Geometry/CNCJob/Document). Holds identity and display name only - no
 * geometry yet. Geometry/parsing lands with the Fase 3 vertical slice
 * (open Gerber -> parse -> display) once flatcam-cam exists.
 */
public final class ProjectItem {

    private final UUID id;
    private final ProjectItemKind kind;
    private final String name;

    public ProjectItem(ProjectItemKind kind, String name) {
        this(UUID.randomUUID(), kind, name);
    }

    public ProjectItem(UUID id, ProjectItemKind kind, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID id() {
        return id;
    }

    public ProjectItemKind kind() {
        return kind;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
