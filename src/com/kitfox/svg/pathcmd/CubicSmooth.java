// My fix of SVGSalamander bug. And390

package com.kitfox.svg.pathcmd;

import com.kitfox.svg.pathcmd.BuildHistory;
import com.kitfox.svg.pathcmd.PathCommand;
import java.awt.geom.GeneralPath;

public class CubicSmooth extends PathCommand {
    public float x = 0.0F;
    public float y = 0.0F;
    public float k2x = 0.0F;
    public float k2y = 0.0F;

    public CubicSmooth() {
    }

    public CubicSmooth(boolean isRelative, float k2x, float k2y, float x, float y) {
        super(isRelative);
        this.k2x = k2x;
        this.k2y = k2y;
        this.x = x;
        this.y = y;
    }

    public void appendPath(GeneralPath path, BuildHistory hist) {
        float offx = this.isRelative?hist.history[0].x:0.0F;
        float offy = this.isRelative?hist.history[0].y:0.0F;
        float oldKx = hist.length >= 2?hist.history[1].x:hist.history[0].x;
        float oldKy = hist.length >= 2?hist.history[1].y:hist.history[0].y;
        float oldX = hist.history[0].x;
        float oldY = hist.history[0].y;
        float k1x = oldX * 2.0F - oldKx;
        float k1y = oldY * 2.0F - oldKy;
        path.curveTo(k1x, k1y, this.k2x + offx, this.k2y + offy, this.x + offx, this.y + offy);
        hist.setPointAndKnot(this.x + offx, this.y + offy, this.k2x + offx, this.k2y + offy);
    }

    public int getNumKnotsAdded() {
        return 6;
    }
}
