package org.prep.day7;

/**
 * LeetCode 200 - Number of Islands.
 * The grid itself IS the graph -- each land cell ('1') is a node, implicitly
 * connected to its up/down/left/right neighbors. Scan every cell; on an
 * unvisited land cell, DFS-flood-fill the whole connected island, sinking
 * ('1' -> '0') every cell it touches so it's never counted again, and
 * increment the island count once per flood-fill call.
 * Time: O(rows * cols) -- every cell visited O(1) times.
 * Space: O(rows * cols) worst case for the DFS call stack (an all-land grid).
 */
public class NumberOfIslands {

    public int numIslands(char[][] grid) {
        int islands = 0;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[0].length; col++) {
                if (grid[row][col] == '1') {
                    sink(grid, row, col);
                    islands++; // one full flood-fill = exactly one island
                }
            }
        }
        return islands;
    }

    private void sink(char[][] grid, int row, int col) {
        boolean outOfBounds = row < 0 || row >= grid.length || col < 0 || col >= grid[0].length;
        if (outOfBounds || grid[row][col] != '1') {
            return; // water, already-sunk land, or off the grid -- stop
        }
        grid[row][col] = '0'; // sink so this cell is never revisited
        sink(grid, row + 1, col);
        sink(grid, row - 1, col);
        sink(grid, row, col + 1);
        sink(grid, row, col - 1);
    }

    public static void main(String[] args) {
        char[][] grid = {
                {'1', '1', '0', '0', '0'},
                {'1', '1', '0', '0', '0'},
                {'0', '0', '1', '0', '0'},
                {'0', '0', '0', '1', '1'}
        };
        System.out.println(new NumberOfIslands().numIslands(grid)); // 3
    }
}
