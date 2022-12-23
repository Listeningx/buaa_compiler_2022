import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static java.lang.Character.isDigit;

public class Error {
    Map<Integer,String> line_errors = new TreeMap<>();
    public Error(){}
    public void output_errors() throws IOException {
        //添加文件输出
        output_to_file();

    }
    public void output_to_file() throws IOException {
        File file = new File("error.txt");
        file.createNewFile();
        StringBuilder error_message = new StringBuilder();
        FileWriter writer = new FileWriter(file);
        for (Iterator<Integer> it = line_errors.keySet().iterator(); it.hasNext();) {
            int line_num = it.next();
            String error_type = line_errors.get(line_num);
            System.out.println(line_num + " " + error_type );
            error_message.append(line_num + " " + error_type + "\n");
        }
        writer.write(error_message.toString());
        writer.close();
    }
    public void addError(int line_num, String error_type){
        if(!this.line_errors.containsKey(line_num))
            this.line_errors.put(line_num,error_type);
    }

}

