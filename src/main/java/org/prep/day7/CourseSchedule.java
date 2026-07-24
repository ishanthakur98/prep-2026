package org.prep.day7;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * LeetCode 207 - Course Schedule.
 * "Can all courses finish?" is exactly "does this directed graph have a cycle?" --
 * a cycle in the prerequisite graph means a course indirectly depends on itself.
 * Solved here with Kahn's algorithm (BFS topological sort): repeatedly peel off
 * nodes with in-degree 0 (no remaining prerequisites). If every node gets peeled
 * off, the graph is a DAG (no cycle) and a valid course order exists; if some
 * nodes are left over, they're stuck in a cycle and can never reach in-degree 0.
 * Time: O(V + E). Space: O(V + E).
 */
public class CourseSchedule {

    public boolean canFinish(int numCourses, int[][] prerequisites) {
        List<List<Integer>> adjacency = new ArrayList<>();
        int[] inDegree = new int[numCourses];
        for (int i = 0; i < numCourses; i++) {
            adjacency.add(new ArrayList<>());
        }
        for (int[] pair : prerequisites) {
            int course = pair[0];
            int prerequisite = pair[1];
            adjacency.get(prerequisite).add(course); // prerequisite -> course
            inDegree[course]++;
        }

        Queue<Integer> queue = new ArrayDeque<>();
        for (int course = 0; course < numCourses; course++) {
            if (inDegree[course] == 0) {
                queue.offer(course); // no prerequisites -- can take immediately
            }
        }

        int taken = 0;
        while (!queue.isEmpty()) {
            int course = queue.poll();
            taken++;
            for (int next : adjacency.get(course)) {
                inDegree[next]--; // this prerequisite is satisfied now
                if (inDegree[next] == 0) {
                    queue.offer(next);
                }
            }
        }

        return taken == numCourses; // fewer than all courses taken -> some are stuck in a cycle
    }

    public static void main(String[] args) {
        CourseSchedule solution = new CourseSchedule();
        System.out.println(solution.canFinish(2, new int[][]{{1, 0}}));         // true: 0 then 1
        System.out.println(solution.canFinish(2, new int[][]{{1, 0}, {0, 1}})); // false: 0 -> 1 -> 0 cycle
    }
}
