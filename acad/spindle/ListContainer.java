package com.bitmechanic.listlib;

import java.util.Iterator;

/**
 * Object that stores information about a given list to iterate through. Your
 * implementation of ListCreator will return one of these.  You only need to
 * set the Iterator and Size properties -- the taglib will set the other
 * properties for you.
 *
 * @version $Id: ListContainer.java,v 1.1 2004/05/07 09:53:25 richard Exp $
 */
public class ListContainer {

    private Iterator _iter;
    private int _max, _start, _end, _size;

    /**
     * Default constructor to allow use via jsp:useBean tag as per
     * servlet spec.    
     * This showed itself as missing during a switch to Tomcat 5.0.19 [RZD]
     */
    public ListContainer()
    {
    }
    
    /**
     * Constructor called by an implementation of ListCreator to create a new
     * ListContainer.  You don't need to set any other properties on the
     * object.
     */
    public ListContainer(Iterator iter, int size) {
        _iter = iter;
        _size = size;
    }

    public void setIterator(Iterator iter) {
        _iter = iter;
    }

    public Iterator getIterator() {
        return _iter;
    }

    public void setSize(int size) {
        _size = size;
    }

    public int getSize() {
        return _size;
    }

    public void setMax(int max) {
        _max = max;
    }

    public int getMax() {
        return _max;
    }

    public int getStart() {
        return _start;
    }

    public int getEnd() {
        return _end;
    }
   
    public void setOffset(int offset) {
        _start = offset + 1;
        _end = Math.min(offset+_max, _size);
    }

    public boolean hasNext() {
        return (_end < _size);
    }

    public boolean hasPrev() {
        return (_start > 1);
    }
}
