package com.prashant.codegen.animations;

import org.springframework.stereotype.Component;

@Component
public class TerminalAnimation {

    private final int WIDTH = 32;
    private final int HEIGHT = 26;

    public void runAnimation(int cycles, int delayMs) {
        // Hide terminal cursor
        System.out.print("\033[?25l");

        try {
            for (int c = 0; c < cycles; c++) {
                for (int frame = 0; frame < 8; frame++) {
                    renderFrame(frame);
                    Thread.sleep(delayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Restore terminal cursor
            System.out.print("\033[?25h\033[0m\n");
            System.out.flush();
        }
    }

    private void renderFrame(int frame) {
        // Create 32x26 frame buffer storing RGB colors
        RGB[][] grid = new RGB[HEIGHT][WIDTH];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid[y][x] = new RGB(0, 0, 0); // Default black background
            }
        }

        // 1. Fire Flame (Flickering)
        RGB fireColor = (frame % 2 == 0) ? new RGB(255, 100, 0) : new RGB(255, 200, 0);
        setPixel(grid, 13, 23, fireColor);
        setPixel(grid, 15, 23, fireColor);
        setPixel(grid, 17, 23, fireColor);
        setPixel(grid, 14, 22, fireColor);
        setPixel(grid, 16, 22, fireColor);

        // 2. Pan
        RGB panColor = new RGB(100, 100, 100);
        for (int x = 11; x <= 19; x++) setPixel(grid, x, 21, panColor);
        setPixel(grid, 10, 20, panColor);
        setPixel(grid, 20, 20, panColor);

        RGB handleColor = new RGB(130, 70, 30);
        for (int x = 21; x <= 26; x++) setPixel(grid, x, 22, handleColor);

        // 3. Food Flipping Logic
        RGB rawColor = new RGB(220, 160, 90);
        RGB midColor = new RGB(180, 115, 55);
        RGB cookedColor = new RGB(140, 70, 20);

        switch (frame % 8) {
            case 0 -> drawPancake(grid, 13, 20, 5, 2, rawColor);
            case 1 -> {
                drawPancake(grid, 13, 17, 5, 2, rawColor);
                drawSteam(grid, 14, 19);
            }
            case 2 -> {
                drawPancake(grid, 14, 13, 3, 3, midColor);
                drawSteam(grid, 13, 16);
            }
            case 3 -> drawPancake(grid, 15, 10, 1, 4, cookedColor);
            case 4 -> drawPancake(grid, 14, 13, 3, 3, cookedColor);
            case 5 -> drawPancake(grid, 13, 17, 5, 2, cookedColor);
            case 6, 7 -> {
                drawPancake(grid, 13, 20, 5, 2, cookedColor);
                if (frame % 2 == 0) {
                    setPixel(grid, 12, 19, new RGB(255, 255, 255));
                    setPixel(grid, 19, 19, new RGB(255, 255, 255));
                }
            }
        }

        // Output grid to terminal using ANSI background colors
        StringBuilder sb = new StringBuilder("\033[H\033[2J"); // Move home & clear
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                RGB color = grid[y][x];
                sb.append(String.format("\033[48;2;%d;%d;%dm  ", color.r, color.g, color.b));
            }
            sb.append("\033[0m\n");
        }

        System.out.print(sb);
        System.out.flush();
    }

    private void drawPancake(RGB[][] grid, int x, int y, int w, int h, RGB color) {
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                setPixel(grid, px, py, color);
            }
        }
    }

    private void drawSteam(RGB[][] grid, int x, int y) {
        RGB steamColor = new RGB(200, 200, 200);
        setPixel(grid, x, y, steamColor);
        setPixel(grid, x + 2, y - 1, steamColor);
    }

    private void setPixel(RGB[][] grid, int x, int y, RGB color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            grid[y][x] = color;
        }
    }

    private record RGB(int r, int g, int b) {}
}