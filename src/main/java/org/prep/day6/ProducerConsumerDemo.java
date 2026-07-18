package org.prep.day6;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Producer-Consumer using BlockingQueue. put()/take() already provide correct
 * backpressure and coordination -- no hand-rolled wait()/notify() needed.
 * See docs/day6/02-java-concurrency-deep.md for the full write-up.
 */
public class ProducerConsumerDemo {

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5); // bounded capacity
        int poisonPill = -1; // sentinel: tells the consumer there's no more work

        Runnable producer = () -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    queue.put(i); // blocks if the queue is full
                    System.out.println("Produced: " + i);
                }
                queue.put(poisonPill);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable consumer = () -> {
            try {
                while (true) {
                    int item = queue.take(); // blocks if the queue is empty
                    if (item == poisonPill) break;
                    System.out.println("Consumed: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread producerThread = new Thread(producer, "producer");
        Thread consumerThread = new Thread(consumer, "consumer");
        producerThread.start();
        consumerThread.start();

        producerThread.join();
        consumerThread.join();
    }
}
