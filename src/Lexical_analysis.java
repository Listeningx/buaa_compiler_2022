import java.util.ArrayList; // 引入 ArrayList 类

public class Lexical_analysis {
    String source_code = "";
    ArrayList<Word> words = new ArrayList<>();
    ArrayList<Line> lines = new ArrayList<>();

    Error la_errors = new Error();
    public Lexical_analysis(String input_txt){
        source_code = input_txt;
        Remove_note rn = new Remove_note(source_code);
        lines = rn.getLines();

        Identify_words iw = new Identify_words(lines);
        words = iw.getWords();
        la_errors = iw.iw_errors;
    }

}

class Remove_note{
    private final String dirty_txt;
    private final ArrayList<Line> lines = new ArrayList<>();

    public Remove_note(String txt){
        this.dirty_txt = txt;
        remove_note();
    }

    //实现去除注释的核心算法
    public void remove_note(){
        int num = 1;//记录行号

        StringBuilder line_buffer = new StringBuilder();
        for(int i = 0; i < dirty_txt.length(); i++){
            char c = dirty_txt.charAt(i);
            //格式字符串状态
            if( c == '\"'){
                do{
                    line_buffer.append(dirty_txt.charAt(i));
                    i++;
                }while(dirty_txt.charAt(i)!='\"');
                line_buffer.append('\"');
            }
            //行的结尾或全部内容的结尾，执行存储操作
            else if(c == '\n'||c == '\r' || i == dirty_txt.length()-1){
                if(c != '\n' && c != '\r'){
                    line_buffer.append(c);
                }
                Line newLine = new Line(line_buffer.toString(),num);
                lines.add(newLine);
                line_buffer = new StringBuilder();
                num++;
            }
            //单行注释状态
            else if(c == '/' && dirty_txt.charAt(i+1) == '/'){
                while((i + 1)<dirty_txt.length()&&dirty_txt.charAt(i + 1)!='\n'&&dirty_txt.charAt(i + 1)!='\r'){
                    i++;
                }
            }
            //多行注释状态
            else if(c == '/' && dirty_txt.charAt(i+1) == '*'){
                i += 2;
                while ((i < dirty_txt.length())){
                    if(dirty_txt.charAt(i) == '*' && dirty_txt.charAt(i+1) == '/'){
                        i++;
                        break;
                    }
                    if(dirty_txt.charAt(i)=='\n'||dirty_txt.charAt(i)=='\r') num++;
                    i++;
                }
            }
            //其他正常字符输入状态
            else if(c != '\r'){
                line_buffer.append(c);
            }
        }
    }

   public ArrayList<Line> getLines(){
        return lines;
   }
}

//用于储存预处理后源码的结构
class Line{
    int line_num;
    String content;
    public Line(String content,int num){
        this.content = content;
        this.line_num = num;
    }
}