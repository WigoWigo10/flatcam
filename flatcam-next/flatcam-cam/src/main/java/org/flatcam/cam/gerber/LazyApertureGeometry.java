package org.flatcam.cam.gerber;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;

/**
 * Read-only per-aperture geometry whose union is computed the first time an
 * aperture is asked for. Only the "Mark" highlight, the editor and project
 * persistence read it, so paying for every union during the parse was wasted
 * work for the common load-and-isolate flow.
 */
final class LazyApertureGeometry extends AbstractMap<String, Geometry> {
    private final Map<String, List<Geometry>> pieces;
    private final Map<String, Geometry> resolved = new LinkedHashMap<>();

    LazyApertureGeometry(Map<String, List<Geometry>> pieces) {
        this.pieces = new LinkedHashMap<>(pieces);
    }

    @Override
    public int size() {
        return pieces.size();
    }

    @Override
    public boolean containsKey(Object key) {
        return pieces.containsKey(key);
    }

    @Override
    public synchronized Geometry get(Object key) {
        List<Geometry> shapes = pieces.get(key);
        if (shapes == null) {
            return null;
        }
        Geometry geometry = resolved.get(key);
        if (geometry == null) {
            geometry = shapes.size() == 1 ? shapes.get(0) : OverlayNGRobust.union(shapes);
            resolved.put((String) key, geometry);
        }
        return geometry;
    }

    @Override
    public Set<Entry<String, Geometry>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<String, Geometry>> iterator() {
                Iterator<String> keys = pieces.keySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return keys.hasNext();
                    }

                    @Override
                    public Entry<String, Geometry> next() {
                        String key = keys.next();
                        return new Entry<>() {
                            @Override
                            public String getKey() {
                                return key;
                            }

                            @Override
                            public Geometry getValue() {
                                return get(key);
                            }

                            @Override
                            public Geometry setValue(Geometry value) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return pieces.size();
            }
        };
    }
}
