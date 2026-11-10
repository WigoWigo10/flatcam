package org.flatcam.cam.ncc;

import java.util.*;
import org.flatcam.cam.CancellationToken;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;

/** Experimental raster-cell ordering. Reorders/reverses existing passes; never changes their coordinates.
 * A split or merge in consecutive scan rows starts new cells. This is a sampled row graph,
 * not an exact polygon decomposition. Finishing rings remain intact at the end. */
final class RegionLinesPlanner {
    private record Pass(LineString line,double low,double high,double row) { }
    private static final class Cell {
        final List<Pass> passes=new ArrayList<>();
        final Set<Cell> neighbors=new LinkedHashSet<>();
    }
    private record Choice(Cell cell,boolean reverseRows,boolean reverseFirst,double distance) { }

    static List<LineString> order(List<LineString> paths,Geometry safe,double angleDegrees,double step,CancellationToken token) {
        if(paths.size()<2)return paths;
        double a=Math.toRadians(angleDegrees),cos=Math.cos(a),sin=Math.sin(a);
        var bounds=safe.getEnvelopeInternal();
        double magnitude=Math.max(Math.max(Math.abs(bounds.getMinX()),Math.abs(bounds.getMaxX())),Math.max(Math.abs(bounds.getMinY()),Math.abs(bounds.getMaxY())));
        double tolerance=Math.max(step*1e-8,Math.ulp(magnitude)*64);
        var raster=new ArrayList<Pass>();var finishing=new ArrayList<LineString>();
        for(var line:paths) {
            token.throwIfCancellationRequested();var p=line.getCoordinates();
            double row=-p[0].x*sin+p[0].y*cos,low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            boolean straight=!line.isClosed();
            for(var c:p){straight&=Math.abs(-c.x*sin+c.y*cos-row)<=tolerance;double x=c.x*cos+c.y*sin;low=Math.min(low,x);high=Math.max(high,x);}
            if(straight)raster.add(new Pass(line,low,high,row));else finishing.add(line);
        }
        raster.sort(Comparator.comparingDouble(Pass::row).reversed().thenComparingDouble(Pass::low));
        var cells=new ArrayList<Cell>();List<Pass> previous=List.of();List<Cell> owners=List.of();
        var prepared=PreparedGeometryFactory.prepare(safe);
        for(int begin=0;begin<raster.size();) {
            token.throwIfCancellationRequested();int end=begin+1;
            while(end<raster.size()&&Math.abs(raster.get(end).row()-raster.get(begin).row())<=tolerance)end++;
            var row=new ArrayList<>(raster.subList(begin,end));row.sort(Comparator.comparingDouble(Pass::low));
            var links=new ArrayList<List<Integer>>();int[] degree=new int[previous.size()];
            int first=0;
            for(var pass:row) {
                token.throwIfCancellationRequested();var parents=new ArrayList<Integer>();
                if(!previous.isEmpty()&&previous.get(0).row()-pass.row()<=step*1.01+tolerance) {
                    while(first<previous.size()&&previous.get(first).high()<pass.low()-tolerance)first++;
                    for(int j=first;j<previous.size()&&previous.get(j).low()<=pass.high()+tolerance;j++) {
                        var old=previous.get(j);double low=Math.max(old.low(),pass.low()),high=Math.min(old.high(),pass.high());
                        if(high-low<=tolerance)continue;
                        double x=(low+high)/2;
                        Coordinate p=new Coordinate(x*cos-old.row()*sin,x*sin+old.row()*cos);
                        Coordinate q=new Coordinate(x*cos-pass.row()*sin,x*sin+pass.row()*cos);
                        if(prepared.covers(safe.getFactory().createLineString(new Coordinate[]{p,q}))){parents.add(j);degree[j]++;}
                    }
                }
                links.add(parents);
            }
            var nextOwners=new ArrayList<Cell>();
            for(int i=0;i<row.size();i++) {
                var parents=links.get(i);Cell cell;
                if(parents.size()==1&&degree[parents.get(0)]==1)cell=owners.get(parents.get(0));
                else {cell=new Cell();cells.add(cell);for(int j:parents){var old=owners.get(j);cell.neighbors.add(old);old.neighbors.add(cell);}}
                cell.passes.add(row.get(i));nextOwners.add(cell);
            }
            previous=new ArrayList<>(row);owners=nextOwners;begin=end;
        }
        var output=new ArrayList<LineString>();var remaining=new LinkedHashSet<>(cells);Coordinate cursor=new Coordinate(0,0);Cell last=null;
        while(!remaining.isEmpty()) {
            token.throwIfCancellationRequested();Choice best=null;
            // Finish one cell before moving to an adjacent unvisited cell; fall back to nearest cell.
            Collection<Cell> candidates=last==null?remaining:last.neighbors.stream().filter(remaining::contains).toList();
            if(candidates.isEmpty())candidates=remaining;
            for(Cell cell:candidates)for(boolean reverseRows:new boolean[]{false,true})for(boolean reverseFirst:new boolean[]{false,true}) {
                var pass=cell.passes.get(reverseRows?cell.passes.size()-1:0);LineString line=orient(pass,reverseFirst,cos,sin);
                double distance=cursor.distance(line.getCoordinateN(0));
                if(best==null||distance<best.distance())best=new Choice(cell,reverseRows,reverseFirst,distance);
            }
            Cell cell=best.cell();for(int i=0;i<cell.passes.size();i++) {
                int j=best.reverseRows()?cell.passes.size()-1-i:i;
                var line=orient(cell.passes.get(j),best.reverseFirst()^((i&1)==1),cos,sin);output.add(line);cursor=line.getCoordinateN(line.getNumPoints()-1);
            }
            remaining.remove(cell);last=cell;
        }
        output.addAll(finishing);return output;
    }
    private static LineString orient(Pass pass,boolean descending,double cos,double sin) {
        Coordinate first=pass.line().getCoordinateN(0),last=pass.line().getCoordinateN(pass.line().getNumPoints()-1);
        boolean isDescending=first.x*cos+first.y*sin>last.x*cos+last.y*sin;
        return isDescending==descending?pass.line():pass.line().reverse();
    }
    private RegionLinesPlanner() { }
}
