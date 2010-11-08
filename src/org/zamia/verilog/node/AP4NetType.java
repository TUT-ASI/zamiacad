/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;

@SuppressWarnings("nls")
public final class AP4NetType extends PNetType
{
    private TKTrior _kTrior_;

    public AP4NetType()
    {
        // Constructor
    }

    public AP4NetType(
        @SuppressWarnings("hiding") TKTrior _kTrior_)
    {
        // Constructor
        setKTrior(_kTrior_);

    }

    @Override
    public Object clone()
    {
        return new AP4NetType(
            cloneNode(this._kTrior_));
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAP4NetType(this);
    }

    public TKTrior getKTrior()
    {
        return this._kTrior_;
    }

    public void setKTrior(TKTrior node)
    {
        if(this._kTrior_ != null)
        {
            this._kTrior_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._kTrior_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._kTrior_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._kTrior_ == child)
        {
            this._kTrior_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._kTrior_ == oldChild)
        {
            setKTrior((TKTrior) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}