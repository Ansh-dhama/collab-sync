package com.example.collab_sync_engine.service;

import com.example.collab_sync_engine.Enum.OperationType;
import org.springframework.stereotype.Service;

@Service
public class DocumentStateService {
    public String applyOperation(
            String currentContent,
            OperationType type,
            int position ,
            String text ,
            Integer length
    ) {
        String safeContent =currentContent==null?"":currentContent;
        if (type == OperationType.NO_OP) {
            return safeContent;
        }
        if(type==OperationType.DELETE){
            return applyDelete(safeContent,position,length);
        }
        if(type==OperationType.INSERT){
            return applyInsert(safeContent,position,text);
        }

        throw new IllegalArgumentException("Invalid operation type");
    }
    public String applyInsert(String currentContent,int position,String text){
          if(text==null || text.isEmpty()){
              throw new IllegalArgumentException("Invalid text");
          }
        if (position < 0 || position > currentContent.length()) {
            throw new IllegalArgumentException(
                    "Invalid insert position: " + position +
                            ". Content length is: " + currentContent.length()
            );
        }
        return currentContent.substring(0,position) + text + currentContent.substring(position);
    }
    public String applyDelete(String currentContent, int position, Integer length){
        if (length == null || length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0 for DELETE operation");
        }

        if (position < 0 || position >= currentContent.length()) {
            throw new IllegalArgumentException(
                    "Invalid delete position: " + position +
                            ". Content length is: " + currentContent.length()
            );
        }
        int endPosition =  position + length;
        if (endPosition > currentContent.length()) {
            throw new IllegalArgumentException(
                    "Delete range is invalid. Position: " + position +
                            ", length: " + length +
                            ", content length: " + currentContent.length()
            );
        }

        return currentContent.substring(0,position)+currentContent.substring(endPosition);
    }

}
