import com.originblue.abstractds.LDRBNode;
import com.originblue.abstractds.LDRBTree;
import com.originblue.abstractds.LDTreap;
import com.originblue.tracking.LDAskOrder;
import com.originblue.tracking.LDBidOrder;
import com.originblue.tracking.LDSequencedMessage;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.*;

public class LDOrderTest {
    @Test
    public void testRBTreeAskDeletionBehavior() {
        // Ask order, so lowest price first...
        LDRBTree<LDAskOrder> rb = new LDRBTree<LDAskOrder>();
        LDAskOrder one = new LDAskOrder("1", new BigDecimal(10.2), new BigDecimal(2));
        LDAskOrder two = new LDAskOrder("2", new BigDecimal(12.2), new BigDecimal(21));
        LDAskOrder three = new LDAskOrder("3", new BigDecimal(13.2), new BigDecimal(223));
        rb.insert(one, "1");
        rb.insert(three, "3");
        rb.insert(two, "2");

        // Need smallest first
        LDRBNode<LDAskOrder> root = rb.treeRoot();
        LDRBNode<LDAskOrder> min = rb.treeMinimum(root);
        assertEquals(min.key.getPrice(), one.getPrice());

        // Make sure all are in
        assertEquals(rb.size(), 3);

        // Now find the next smallest by id and remove it
        LDRBNode<LDAskOrder> secondSmallestNode = rb.lookupBySecondaryKey("2");
        rb.remove(secondSmallestNode, secondSmallestNode.key.getOrderId());

        // Now make sure it is missing
        assertEquals(rb.size(), 2);
        assertEquals(rb.lookupBySecondaryKey("2"), null);

        // And make sure we will get 3 as the next greatest
        assertEquals(rb.getGreaterThan(one, rb.size()).get(0).getOrderId(), "3");
    }

    @Test
    public void testLDMessageQueueOrdering() {
        LDRBTree<LDSequencedMessage> rb = new LDRBTree<>();
        LDSequencedMessage eleven = new LDSequencedMessage(BigInteger.valueOf(11));
        LDSequencedMessage one = new LDSequencedMessage(BigInteger.valueOf(1));
        LDSequencedMessage five = new LDSequencedMessage(BigInteger.valueOf(5));
        LDSequencedMessage three = new LDSequencedMessage(BigInteger.valueOf(3));
        rb.insert(eleven, "11");
        rb.insert(one, "1");
        rb.insert(five, "5");
        rb.insert(three, "3");

        // Need smallest first
        LDRBNode<LDSequencedMessage> root = rb.treeRoot();
        LDRBNode<LDSequencedMessage> min = rb.treeMinimum(root);
        assertEquals(min.key.sequenceId, one.sequenceId);

        List<LDSequencedMessage> greaterThan = rb.getGreaterThan(min.key, rb.size());
        assertEquals(greaterThan.size(), 3);
        assertEquals(greaterThan.get(0).sequenceId, BigInteger.valueOf(3));
        assertEquals(greaterThan.get(1).sequenceId, BigInteger.valueOf(5));
        assertEquals(greaterThan.get(2).sequenceId, BigInteger.valueOf(11));
    }

    @Test
    public void testLDAskOrderSorting() {
        LDRBTree<LDAskOrder> rb = new LDRBTree<>();
        LDAskOrder one = new LDAskOrder("1", new BigDecimal(10.2), new BigDecimal(2));
        LDAskOrder two = new LDAskOrder("1", new BigDecimal(12.2), new BigDecimal(2));
        LDAskOrder three = new LDAskOrder("1", new BigDecimal(13.2), new BigDecimal(2));
        rb.insert(one, "1");
        rb.insert(three, "3");
        rb.insert(two, "2");

        // Need smallest first
        LDRBNode<LDAskOrder> root = rb.treeRoot();
        LDRBNode<LDAskOrder> min = rb.treeMinimum(root);
        assertEquals(min.key.getPrice(), one.getPrice());

        List<LDAskOrder> greaterThan = rb.getGreaterThan(min.key, 2);
        assertEquals(greaterThan.size(), 2);
        assertEquals(greaterThan.get(0).getPrice(), two.getPrice());
        assertEquals(greaterThan.get(1).getPrice(), three.getPrice());
    }

    @Test
    public void testLDBidOrderSorting() {
        LDRBTree<LDBidOrder> rb = new LDRBTree<>();
        LDBidOrder one = new LDBidOrder("1", new BigDecimal(10.2), new BigDecimal(2));
        LDBidOrder two = new LDBidOrder("1", new BigDecimal(12.2), new BigDecimal(2));
        LDBidOrder three = new LDBidOrder("1", new BigDecimal(13.2), new BigDecimal(2));
        rb.insert(one, "1");
        rb.insert(three, "3");
        rb.insert(two, "2");

        // Need smallest first
        LDRBNode<LDBidOrder> root = rb.treeRoot();
        LDRBNode<LDBidOrder> max = rb.treeMinimum(root);
        assertEquals(max.key.getPrice(), three.getPrice());

        List<LDBidOrder> greaterThan = rb.getGreaterThan(max.key, 2);
        assertEquals(greaterThan.size(), 2);
        assertEquals(greaterThan.get(0).getPrice(), two.getPrice());
        assertEquals(greaterThan.get(1).getPrice(), one.getPrice());
    }

    @Test
    public void testLDTreapDuplicates() {
        LDTreap<LDBidOrder> treap = new LDTreap<LDBidOrder>();
        LDBidOrder one = new LDBidOrder("1", new BigDecimal(10.1), new BigDecimal(1));
        LDBidOrder onehalf = new LDBidOrder("2", new BigDecimal(10.1), new BigDecimal(2));
        LDBidOrder two = new LDBidOrder("3", new BigDecimal(10.23), new BigDecimal(4));

        treap.insert(onehalf);
        treap.insert(one);
        treap.insert(two);

        assertEquals(treap.size(), 3);

        LDBidOrder best = treap.findBest();
        assertEquals(best.getPrice(), two.getPrice());
        treap.remove(best);

        // Now we expect to see the onehalf
        best = treap.findBest();
        assertEquals(best.getPrice(), one.getPrice());
        BigDecimal size = best.getSize();
        treap.remove(best);

        // Now we hope to see the one
        best = treap.findBest();
        assertEquals(best.getPrice(), onehalf.getPrice());
        assertEquals(
                onehalf.getSize().add(one.getSize()),
                best.getSize().add(size));
        treap.remove(best);
    }

    @Test
    public void testLDTreapLDBidOrderSorting() {
        LDTreap<LDBidOrder> treap = new LDTreap<LDBidOrder>();
        LDBidOrder one = new LDBidOrder("1", new BigDecimal(10.2), new BigDecimal(2));
        LDBidOrder onehalf = new LDBidOrder("1", new BigDecimal(10.22), new BigDecimal(2));
        LDBidOrder tt = new LDBidOrder("123", new BigDecimal(10.22), new BigDecimal(123));
        LDBidOrder two = new LDBidOrder("1", new BigDecimal(12.2), new BigDecimal(2));
        LDBidOrder three = new LDBidOrder("1", new BigDecimal(13.2), new BigDecimal(2));
        treap.insert(one);
        treap.insert(three);
        treap.insert(onehalf);
        treap.insert(two);
        treap.insert(tt);

        assertEquals(treap.findBest().getPrice(), three.getPrice());
        treap.remove(three);

        LDBidOrder ndBidOrder = treap.constantLookup("123");
        assertNotNull(ndBidOrder);
        treap.remove(ndBidOrder);

        assertEquals(treap.findBest().getPrice(), two.getPrice());
        treap.remove(two);

        assertTrue(treap.contains(onehalf));
        treap.remove(onehalf);
        assertFalse(treap.contains(onehalf));

        assertEquals(treap.findBest().getPrice(), one.getPrice());
        treap.remove(one);
    }

    @Test
    public void testLDTreapLDAskOrderSorting() {
        LDTreap<LDAskOrder> treap = new LDTreap<LDAskOrder>();
        LDAskOrder one = new LDAskOrder("11111111", new BigDecimal(10.2), new BigDecimal(2));
        LDAskOrder onehalf = new LDAskOrder("2231", new BigDecimal(10.22), new BigDecimal(2));
        LDAskOrder two = new LDAskOrder("1111", new BigDecimal(12.2), new BigDecimal(2));
        LDAskOrder three = new LDAskOrder("1123", new BigDecimal(13.2), new BigDecimal(2));
        LDAskOrder big = new LDAskOrder("huge", BigDecimal.valueOf(139999.2), new BigDecimal(2));
        treap.insert(one);
        treap.insert(three);
        treap.insert(onehalf);
        treap.insert(big);
        treap.insert(two);

        assertEquals(treap.findBest().getPrice(), one.getPrice());
        treap.remove(one);

        assertTrue(BigDecimal.valueOf(139999.2).subtract(treap.constantLookup("huge").getPrice()).doubleValue() == 0);

        assertEquals(treap.findBest().getPrice(), onehalf.getPrice());
        treap.remove(onehalf);

        assertTrue(treap.contains(two));
        treap.remove(two);
        assertFalse(treap.contains(two));

        assertEquals(treap.findBest().getPrice(), three.getPrice());
        treap.remove(three);
    }

}