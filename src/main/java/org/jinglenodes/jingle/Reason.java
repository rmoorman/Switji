/*
 * Copyright (C) 2011 - Jingle Nodes - Yuilop - Neppo
 *
 *   This file is part of Switji (http://jinglenodes.org)
 *
 *   Switji is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation; either version 2 of the License, or
 *   (at your option) any later version.
 *
 *   Switji is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with MjSip; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *   Author(s):
 *   Benhur Langoni (bhlangonijr@gmail.com)
 *   Thiago Camargo (barata7@gmail.com)
 */

package org.jinglenodes.jingle;

import org.dom4j.Element;
import org.dom4j.tree.BaseElement;

public class Reason extends BaseElement {

    private Type type;
    private static final String NAME = "reason";
    private static final String TEXT = "text";

    public static Reason fromElement(Element element) {
        //TODO
        return null;  //To change body of created methods use File | Settings | File Templates.
    }

    public enum Type {
        security_error, alternative_session, busy, connectivity_error, decline, general_error, media_error, no_error, success, unsupported_applications, unsupported_transports, timeout;

        public String toString() {
            return this.name().replace('_', '-');
        }
    }

    public Reason(final Type type) {
        this(null, type);
    }

    public Reason(final String text, final Type type) {
        super(NAME);
        this.type = type;
        this.addElement(type.toString());
        if (null != text)
            this.addElement(TEXT).addCDATA(text);
    }

    public String getText() {
        if (null != this.element(TEXT))
            return this.element(TEXT).getStringValue();
        return null;
    }

    public void setText(final String text) {
        if (null != text)
            this.addElement(TEXT).addCDATA(text);
    }

    public Type getType() {
        return this.type;
    }

    public void setType(final Type type) {
        this.type = type;
        //TODO remove old element
        this.addElement(type.toString());
    }
}
