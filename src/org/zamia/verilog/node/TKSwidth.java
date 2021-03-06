/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;
import org.zamia.SourceFile;

@SuppressWarnings("nls")
public final class TKSwidth extends Token
{
    public TKSwidth(int line, int pos, SourceFile sf)
    {
        super ("$width", line, pos, sf);
    }

    @Override
    public Object clone()
    {
      return new TKSwidth(getLine(), getPos(), getSource());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTKSwidth(this);
    }

    @Override
    public void setText(@SuppressWarnings("unused") String text)
    {
        throw new RuntimeException("Cannot change TKSwidth text.");
    }
}
