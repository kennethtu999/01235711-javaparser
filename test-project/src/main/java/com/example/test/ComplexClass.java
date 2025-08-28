package com.example.test;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ComplexClass {
    private static final Logger logger = Logger.getLogger(ComplexClass.class.getName());
    private List<String> dataList;
    
    public ComplexClass() {
        this.dataList = new ArrayList<>();
        logger.info("ComplexClass initialized");
    }
    
    public void processData(String input) {
        logger.fine("Processing input: " + input);
        
        if (input != null && !input.isEmpty()) {
            String processed = input.trim().toUpperCase();
            dataList.add(processed);
            logger.info("Added processed data: " + processed);
        } else {
            logger.warning("Invalid input received");
        }
    }
    
    public List<String> getDataList() {
        logger.fine("Retrieving data list, size: " + dataList.size());
        return new ArrayList<>(dataList);
    }
    
    public void clearData() {
        logger.info("Clearing data list");
        dataList.clear();
    }
    
    public static void main(String[] args) {
        ComplexClass instance = new ComplexClass();
        instance.processData("hello world");
        instance.processData("test data");
        
        List<String> result = instance.getDataList();
        System.out.println("Result: " + result);
        
        instance.clearData();
    }
}
