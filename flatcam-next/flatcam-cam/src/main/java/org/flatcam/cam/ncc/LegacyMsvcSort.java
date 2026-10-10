/*
 * Copyright (c) Microsoft Corporation.
 * SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
 *
 * Java adaptation of the VS 2019 STL sort/heap algorithms, with cancellation.
 * Source: microsoft/STL, vs-2019-16.10, stl/inc/algorithm and xutility.
 * License distributed in META-INF/licenses/microsoft-stl.txt.
 */
package org.flatcam.cam.ncc;

import java.util.Comparator;
import java.util.List;
import org.flatcam.cam.CancellationToken;

/** Legacy Windows ordering, including equal keys; not a general application sorter. */
final class LegacyMsvcSort<T> {
    private final List<T> values;
    private final Comparator<T> comparator;
    private final CancellationToken cancellation;

    private LegacyMsvcSort(List<T> values, Comparator<T> comparator, CancellationToken cancellation) {
        this.values = values;
        this.comparator = comparator;
        this.cancellation = cancellation;
    }

    static <T> void sort(List<T> values, Comparator<T> comparator, CancellationToken cancellation) {
        new LegacyMsvcSort<>(values, comparator, cancellation).sort(0, values.size(), values.size());
    }

    private void sort(int first, int last, int budget) {
        while (true) {
            cancellation.throwIfCancellationRequested();
            if (last - first <= 32) {
                for (int i = first + 1; i < last; i++) {
                    T value = values.get(i);
                    int hole = i;
                    while (hole > first && comparator.compare(value, values.get(hole - 1)) < 0) {
                        values.set(hole, values.get(hole - 1));
                        hole--;
                    }
                    values.set(hole, value);
                }
                return;
            }
            if (budget <= 0) {
                heapSort(first, last);
                return;
            }
            int middle = first + (last - first) / 2;
            guessMedian(first, middle, last - 1);
            int[] pivot = partition(first, middle, last);
            budget = (budget >> 1) + (budget >> 2);
            if (pivot[0] - first < last - pivot[1]) {
                sort(first, pivot[0], budget);
                first = pivot[1];
            } else {
                sort(pivot[1], last, budget);
                last = pivot[0];
            }
        }
    }

    private void guessMedian(int first, int middle, int last) {
        if (last - first > 40) {
            int step = (last - first + 1) >> 3;
            median(first, first + step, first + 2 * step);
            median(middle - step, middle, middle + step);
            median(last - 2 * step, last - step, last);
            median(first + step, middle, last - step);
        } else {
            median(first, middle, last);
        }
    }

    private void median(int first, int middle, int last) {
        if (less(middle, first)) swap(middle, first);
        if (less(last, middle)) {
            swap(last, middle);
            if (less(middle, first)) swap(middle, first);
        }
    }

    private int[] partition(int first, int pivotFirst, int last) {
        int pivotLast = pivotFirst + 1;
        while (first < pivotFirst && !less(pivotFirst - 1, pivotFirst) && !less(pivotFirst, pivotFirst - 1)) pivotFirst--;
        while (pivotLast < last && !less(pivotLast, pivotFirst) && !less(pivotFirst, pivotLast)) pivotLast++;
        int upper = pivotLast;
        int lower = pivotFirst;
        while (true) {
            cancellation.throwIfCancellationRequested();
            for (; upper < last; upper++) {
                if (less(pivotFirst, upper)) continue;
                if (less(upper, pivotFirst)) break;
                if (pivotLast != upper) swap(pivotLast, upper);
                pivotLast++;
            }
            for (; first < lower; lower--) {
                if (less(lower - 1, pivotFirst)) continue;
                if (less(pivotFirst, lower - 1)) break;
                if (--pivotFirst != lower - 1) swap(pivotFirst, lower - 1);
            }
            if (lower == first && upper == last) return new int[]{pivotFirst, pivotLast};
            if (lower == first) {
                if (pivotLast != upper) swap(pivotFirst, pivotLast);
                pivotLast++;
                swap(pivotFirst, upper);
                pivotFirst++;
                upper++;
            } else if (upper == last) {
                if (--lower != --pivotFirst) swap(lower, pivotFirst);
                swap(pivotFirst, --pivotLast);
            } else {
                swap(upper, --lower);
                upper++;
            }
        }
    }

    private void heapSort(int first, int last) {
        int size = last - first;
        for (int hole = size / 2; hole > 0;) {
            cancellation.throwIfCancellationRequested();
            --hole;
            heapHole(first, hole, size, values.get(first + hole));
        }
        while (size > 1) {
            cancellation.throwIfCancellationRequested();
            T value = values.get(first + --size);
            values.set(first + size, values.get(first));
            heapHole(first, 0, size, value);
        }
    }

    private void heapHole(int first, int hole, int size, T value) {
        int top = hole;
        int index = hole;
        int lastParent = (size - 1) >> 1;
        while (index < lastParent) {
            index = 2 * index + 2;
            if (less(first + index, first + index - 1)) --index;
            values.set(first + hole, values.get(first + index));
            hole = index;
        }
        if (index == lastParent && size % 2 == 0) {
            values.set(first + hole, values.get(first + size - 1));
            hole = size - 1;
        }
        while (top < hole) {
            int parent = (hole - 1) >> 1;
            if (comparator.compare(values.get(first + parent), value) >= 0) break;
            values.set(first + hole, values.get(first + parent));
            hole = parent;
        }
        values.set(first + hole, value);
    }

    private boolean less(int left, int right) {
        return comparator.compare(values.get(left), values.get(right)) < 0;
    }

    private void swap(int left, int right) {
        T value = values.get(left);
        values.set(left, values.get(right));
        values.set(right, value);
    }
}
