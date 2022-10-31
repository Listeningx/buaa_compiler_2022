import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static java.lang.Character.isDigit;

public class Error {
    StringBuilder error_message = new StringBuilder();
    Map<Integer,String> line_errors = new LinkedHashMap<>();
    public Error(){}
    public void output_errors() throws IOException {
//        map2String();
        System.out.println(error_message);

        //添加文件输出
        output_to_file();

    }
    public void output_to_file() throws IOException {
        File file = new File("error.txt");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(error_message.toString());
        writer.close();
    }
    public void addError(int line_num, String error_type){
        if(!this.line_errors.containsKey(line_num))
            this.line_errors.put(line_num,error_type);
    }
    public String map2String(){
        StringBuilder str = new StringBuilder();
        for(Map.Entry<Integer,String> entry : line_errors.entrySet()){
           str.append(entry.getKey() + " " + entry.getValue() + "\n");
        }
        return str.toString();
    }
}
