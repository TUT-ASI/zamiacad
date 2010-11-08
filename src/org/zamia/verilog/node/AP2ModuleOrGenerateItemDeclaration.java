/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.zamia.verilog.node;

import org.zamia.verilog.analysis.*;

@SuppressWarnings("nls")
public final class AP2ModuleOrGenerateItemDeclaration extends PModuleOrGenerateItemDeclaration
{
    private PIntegerDeclaration _integerDeclaration_;

    public AP2ModuleOrGenerateItemDeclaration()
    {
        // Constructor
    }

    public AP2ModuleOrGenerateItemDeclaration(
        @SuppressWarnings("hiding") PIntegerDeclaration _integerDeclaration_)
    {
        // Constructor
        setIntegerDeclaration(_integerDeclaration_);

    }

    @Override
    public Object clone()
    {
        return new AP2ModuleOrGenerateItemDeclaration(
            cloneNode(this._integerDeclaration_));
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseAP2ModuleOrGenerateItemDeclaration(this);
    }

    public PIntegerDeclaration getIntegerDeclaration()
    {
        return this._integerDeclaration_;
    }

    public void setIntegerDeclaration(PIntegerDeclaration node)
    {
        if(this._integerDeclaration_ != null)
        {
            this._integerDeclaration_.parent(null);
        }

        if(node != null)
        {
            if(node.parent() != null)
            {
                node.parent().removeChild(node);
            }

            node.parent(this);
        }

        this._integerDeclaration_ = node;
    }

    @Override
    public String toString()
    {
        return ""
            + toString(this._integerDeclaration_);
    }

    @Override
    void removeChild(@SuppressWarnings("unused") Node child)
    {
        // Remove child
        if(this._integerDeclaration_ == child)
        {
            this._integerDeclaration_ = null;
            return;
        }

        throw new RuntimeException("Not a child.");
    }

    @Override
    void replaceChild(@SuppressWarnings("unused") Node oldChild, @SuppressWarnings("unused") Node newChild)
    {
        // Replace child
        if(this._integerDeclaration_ == oldChild)
        {
            setIntegerDeclaration((PIntegerDeclaration) newChild);
            return;
        }

        throw new RuntimeException("Not a child.");
    }
}