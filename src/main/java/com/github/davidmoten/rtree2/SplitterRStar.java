package com.github.davidmoten.rtree2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.github.davidmoten.guavamini.Preconditions;
import com.github.davidmoten.guavamini.annotations.VisibleForTesting;
import com.github.davidmoten.rtree2.geometry.HasGeometry;
import com.github.davidmoten.rtree2.geometry.ListPair;

public final class SplitterRStar implements Splitter {

    private final Comparator<ListPair<?>> comparator;

    public SplitterRStar() {
        this.comparator = new Comparator<ListPair<?>>() {

            @Override
            public int compare(ListPair<?> p1, ListPair<?> p2) {
                // check overlap first then areaSum
                int value = Double.compare(overlap(p1), overlap(p2));
                if (value == 0) {
                    return Double.compare(p1.areaSum(), p2.areaSum());
                } else {
                    return value;
                }
            }
        };
    }

    private static final boolean[] BOOLEANS = new boolean[] { false, true };

    @Override
    public <T extends HasGeometry> ListPair<T> split(List<T> items, int minSize) {
        Preconditions.checkArgument(!items.isEmpty());
        // sort nodes into increasing x, calculate min overlap where both groups
        // have more than minChildren

        // compute S the sum of all margin-values of the lists above
        // the list with the least S is then used to find minimum overlap

        List<ListPair<T>> pairs = null;
        float lowestMarginSum = Float.MAX_VALUE;
        List<T> list = null;
        for (int i = 0; i < items.get(0).geometry().dimensions(); i++) {
            for (boolean isUpper : BOOLEANS) {
                if (list == null) {
                    list = new ArrayList<T>(items);
                }
                Collections.sort(list, comparator(i, isUpper));
                List<ListPair<T>> p = getPairs(minSize, list);
                float marginSum = marginValueSum(p);
                if (marginSum < lowestMarginSum) {
                    lowestMarginSum = marginSum;
                    pairs = p;
                    // because p uses subViews of list we need to create a new one
                    // for further comparisons
                    list = null;
                }
            }
        }
        return Collections.min(pairs, comparator);
    }

    private static Comparator<HasGeometry> comparator(int dimension, boolean upper) {
        if (upper) {
            return (a, b) -> Double.compare(a.geometry().mbr().maxes()[dimension], b.geometry().mbr().maxes()[dimension]);
        } else {
            return (a, b) -> Double.compare(a.geometry().mbr().mins()[dimension], b.geometry().mbr().mins()[dimension]);
        }
    }

    private static <T extends HasGeometry> float marginValueSum(List<ListPair<T>> list) {
        float sum = 0;
        for (ListPair<T> p : list)
            sum += p.marginSum();
        return sum;
    }

    @VisibleForTesting
    static <T extends HasGeometry> List<ListPair<T>> getPairs(int minSize, List<T> list) {
        List<ListPair<T>> pairs = new ArrayList<ListPair<T>>(list.size() - 2 * minSize + 1);
        for (int i = minSize; i < list.size() - minSize + 1; i++) {
            // Note that subList returns a view of list so creating list1 and
            // list2 doesn't necessarily incur array allocation costs.
            List<T> list1 = list.subList(0, i);
            List<T> list2 = list.subList(i, list.size());
            ListPair<T> pair = new ListPair<T>(list1, list2);
            pairs.add(pair);
        }
        return pairs;
    }

    private static double overlap(ListPair<? extends HasGeometry> pair) {
        return pair.group1().geometry().mbr().intersectionArea(pair.group2().geometry().mbr());
    }

}
