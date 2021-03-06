/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;
import org.zamia.SourceFile;

@SuppressWarnings("nls")
public final class TKInout extends Token
{
    public TKInout(int line, int pos, SourceFile sf)
    {
        super ("inout", line, pos, sf);
    }

    @Override
    public Object clone()
    {
      return new TKInout(getLine(), getPos(), getSource());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTKInout(this);
    }

    @Override
    public void setText(@SuppressWarnings("unused") String text)
    {
        throw new RuntimeException("Cannot change TKInout text.");
    }
}
