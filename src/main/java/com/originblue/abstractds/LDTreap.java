package com.originblue.abstractds;

// ******************PUBLIC OPERATIONS*********************
// void insert( x )       --> Insert x
// void remove( x )       --> Remove x
// boolean contains( x )  --> Return true if x is found
// Comparable findBest( )  --> Return smallest item
// Comparable findWorst( )  --> Return largest item
// boolean isEmpty( )     --> Return true if empty; else false
// void makeEmpty( )      --> Remove all items
// Comparable constantLookup() --> constant time lookup, bitches

import java.util.*;
import java.util.concurrent.Semaphore;

public class LDTreap<AnyType extends Comparable<? super AnyType>> {
    private Semaphore semaphore;
    private Map<String, AnyType> lookupTable;

    /**
     * Construct the treap.
     */
    public LDTreap() {
        lookupTable = new HashMap<String, AnyType>();
        semaphore = new Semaphore(1);
        nullNode = new TreapNode<AnyType>(null);
        nullNode.left = nullNode.right = nullNode;
        nullNode.priority = Integer.MAX_VALUE;
        root = nullNode;
    }

    /**
     * Insert into the tree. Does nothing if x is already present.
     *
     * @param x the item to insert.
     */
    public void insert(AnyType x) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        root = insert(x, root);
        lookupTable.put(x.toString(), x);
        semaphore.release();
    }

    /**
     * Remove from the tree. Does nothing if x is not found.
     *
     * @param x the item to remove.
     */
    public void remove(AnyType x) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        root = remove(x, root);
        lookupTable.remove(x.toString());
        semaphore.release();
    }

    /**
     * Find the smallest item in the tree.
     *
     * @return the smallest item, or null if empty.
     */
    public AnyType findBest() {
        if (isEmpty())
            return null;

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        TreapNode<AnyType> ptr = root;

        while (ptr.left != nullNode)
            ptr = ptr.left;

        semaphore.release();
        return ptr.element;
    }

    /**
     * Find the largest item in the tree.
     *
     * @return the largest item, or null if empty.
     */
    public AnyType findWorst() {
        if (isEmpty())
            return null;

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        TreapNode<AnyType> ptr = root;

        while (ptr.right != nullNode)
            ptr = ptr.right;

        semaphore.release();
        return ptr.element;
    }

    /**
     * Find an item in the tree.
     *
     * @param x the item to search for.
     * @return true if x is found.
     */
    public boolean contains(AnyType x) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        TreapNode<AnyType> current = root;
        nullNode.element = x;

        for (; ; ) {
            int compareResult = x.compareTo(current.element);

            if (compareResult < 0)
                current = current.left;
            else if (compareResult > 0)
                current = current.right;
            else {
                semaphore.release();
                return current != nullNode;
            }
        }
    }

    public AnyType constantLookup(String key) {
        if (lookupTable == null || key == null)
            return null;
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        AnyType toReturn = lookupTable.get(key);
        semaphore.release();
        return toReturn;
    }

    public int size() {
        return this.lookupTable.size();
    }

    /**
     * Make the tree logically empty.
     */
    public void makeEmpty() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        root = nullNode;
        lookupTable.clear();
        semaphore.release();
    }

    /**
     * Test if the tree is logically empty.
     *
     * @return true if empty, false otherwise.
     */
    public boolean isEmpty() {
        return root == nullNode;
    }

    public List<AnyType> getItems() {
        List<AnyType> all = new ArrayList<>();
        if (isEmpty() || root == null)
            return all;
        _getItems(all, root);
        return all;
    }

    private void _getItems(List<AnyType> acc, TreapNode<AnyType> t) {
        if (t != t.left) {
            _getItems(acc, t.left);
            acc.add(t.element);
            _getItems(acc, t.right);
        }
    }


    /**
     * Internal method to insert into a subtree.
     *
     * @param x the item to insert.
     * @param t the node that roots the subtree.
     * @return the new root of the subtree.
     */
    private TreapNode<AnyType> insert(AnyType x, TreapNode<AnyType> t) {
        if (t == nullNode)
            return new TreapNode<AnyType>(x, nullNode, nullNode);

        int compareResult = x.compareTo(t.element);

        if (compareResult < 0) {
            t.left = insert(x, t.left);
            if (t.left.priority < t.priority)
                t = rotateWithLeftChild(t);
        } else if (compareResult > 0) {
            t.right = insert(x, t.right);
            if (t.right.priority < t.priority)
                t = rotateWithRightChild(t);
        } else {
            t.next = new TreapNode<AnyType>(x);
        }

        return t;
    }

    /**
     * Internal method to remove from a subtree.
     *
     * @param x the item to remove.
     * @param t the node that roots the subtree.
     * @return the new root of the subtree.
     */
    private TreapNode<AnyType> remove(AnyType x, TreapNode<AnyType> t) {
        if (t != nullNode) {
            int compareResult = x.compareTo(t.element);

            if (compareResult < 0)
                t.left = remove(x, t.left);
            else if (compareResult > 0)
                t.right = remove(x, t.right);
            else {
                // Match found, check if it is a single value
                if (t.next == null) {
                    if (t.left.priority < t.right.priority)
                        t = rotateWithLeftChild(t);
                    else
                        t = rotateWithRightChild(t);

                    if (t != nullNode)     // Continue on down
                        t = remove(x, t);
                    else
                        t.left = nullNode;  // At a leaf
                } else {
                    // Now, the match can be in the beginning or in the middle or in the end
                    // If it is on the end, we will simply remove it.
                    if (t.element.toString().equals(x.toString())) {
                        t.element = t.next.element;
                        t.next = t.next.next;
                        return t;
                    }

                    // Start by iterating over it
                    TreapNode<AnyType> curr = t;
                    while (curr.next != null && !curr.next.element.toString().equals(x.toString())) {
                        curr = curr.next;
                    }
                    // Nothing to see here.
                    if (curr.next == null)
                        return t;

                    // curr.next.elements has our match, we will remove it from chain
                    curr.next = curr.next.next;
                }
            }
        }
        return t;
    }


    /**
     * Rotate binary tree node with left child.
     */
    private TreapNode<AnyType> rotateWithLeftChild(TreapNode<AnyType> k2) {
        TreapNode<AnyType> k1 = k2.left;
        k2.left = k1.right;
        k1.right = k2;
        return k1;
    }

    /**
     * Rotate binary tree node with right child.
     */
    private TreapNode<AnyType> rotateWithRightChild(TreapNode<AnyType> k1) {
        TreapNode<AnyType> k2 = k1.right;
        k1.right = k2.left;
        k2.left = k1;
        return k2;
    }

    private static class TreapNode<AnyType> {
        // Constructors
        TreapNode(AnyType theElement) {
            this(theElement, null, null);
        }

        TreapNode(AnyType theElement, TreapNode<AnyType> lt, TreapNode<AnyType> rt) {
            next = null;
            element = theElement;
            left = lt;
            right = rt;
            priority = randomObj.nextInt();
        }

        // Friendly data; accessible by other package routines
        AnyType element;      // The data in the node
        TreapNode<AnyType> left;         // Left child
        TreapNode<AnyType> right;        // Right child
        TreapNode<AnyType> next;         // Next of same comprable value
        int priority;     // Priority

        private static Random randomObj = new Random();
    }

    private TreapNode<AnyType> root;
    private TreapNode<AnyType> nullNode;
}
