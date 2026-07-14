package org.prep.designPattern.strategy;

public class EmailNotification implements NotificationSender{

    @Override
    public void send(String to, String message) {
        // TODO Auto-generated method stub
        System.out.printf("%s sent email message as %s",to,message);
    }
    
}
