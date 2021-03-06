/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;

@SuppressWarnings("nls")
public final class AP0Lvalue extends PLvalue
{
    private PHierarchicalIdentifierMb _hierarchicalIdentifierMb_;

    public AP0Lvalue()
    {
        // Constructor
    }

    public AP0Lvalue(
        @SuppressWarnings("hiding") PHierarchicalIdentifierMb _hierarchicalIdentifierMb_)
    {
        // Constructor
        setHierarchicalIdentifierMb(_hierarchicalIdentifierMb_);

    }

    @Override
    public Object clone()
    {
        return new AP0Lvalue(
            cloneNode(this._hierarchicalIdentifierMb_));
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAP0Lvalue(this);
    }

    public PHierarchicalIdentifierMb getHierarchicalIdentifierMb()
    {
        return this._hierarchicalIdentifierMb_;
    }

    public void setHierarchicalIdentifierMb(PHierarchicalIdentifierMb node)
    {
        if(this._hierarchicalIdentifierMb_ != null)
        {
            this._hierarchicalIdentifierMb_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._hierarchicalIdentifierMb_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._hierarchicalIdentifierMb_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._hierarchicalIdentifierMb_ == child)
        {
            this._hierarchicalIdentifierMb_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._hierarchicalIdentifierMb_ == oldChild)
        {
            setHierarchicalIdentifierMb((PHierarchicalIdentifierMb) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}
