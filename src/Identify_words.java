import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static java.lang.Character.*;

public class Identify_words {
    private final ArrayList<Word> words = new ArrayList<>();
    private ArrayList<Line> lines = new ArrayList<>();
    private Map content2type = new HashMap<>();
    Error iw_errors = new Error();
    public Identify_words(ArrayList<Line> lines){
        this.lines = lines;
        init_content2Type();
        identify_words();
    }
    public Identify_words(){
       init_content2Type();
    }
    public void init_content2Type(){
        Map types = new HashMap<String,String>();
        types.put("main","MAINTK");
        types.put("const","CONSTTK");
        types.put("int","INTTK");
        types.put("break","BREAKTK");
        types.put("continue","CONTINUETK");
        types.put("if","IFTK");
        types.put("else","ELSETK");
        types.put("!","NOT");
        types.put("while","WHILETK");
        types.put("getint","GETINTTK");
        types.put("printf","PRINTFTK");
        types.put("return","RETURNTK");
        types.put("+","PLUS");
        types.put("-","MINU");
        types.put("void","VOIDTK");
        types.put("*","MULT");
        types.put("/","DIV");
        types.put("%","MOD");
        types.put("<","LSS");
        types.put(">","GRE");
        types.put("=","ASSIGN");
        types.put(";","SEMICN");
        types.put(",","COMMA");
        types.put("(","LPARENT");
        types.put(")","RPARENT");
        types.put("[","LBRACK");
        types.put("]","RBRACK");
        types.put("{","LBRACE");
        types.put("}","RBRACE");
        this.content2type = types;
    }

    //识别单词的核心算法
    public void identify_words(){
        for(int i = 0; i < lines.size(); i++){
            Line thisLine = lines.get(i);
            String txt = thisLine.content;//取出本行的内容

            for(int j = 0; j < txt.length(); j++){
                while(j < txt.length()&&(txt.charAt(j)==' '||txt.charAt(j)=='\t')){
                    j++;
                }
                if(j >= txt.length()) break;

                //标识符或关键字
                if(txt.charAt(j) == '_'|| isLetter(txt.charAt(j))){
                    Identifier word = new Identifier();
                    StringBuilder word_buffer = new StringBuilder();
                    while(j < txt.length()&&(txt.charAt(j) == '_'|| isLetter(txt.charAt(j))||isDigit(txt.charAt(j)))){
                        word_buffer.append(txt.charAt(j));
                        j++;
                    }j--;
                    word.content = word_buffer.toString();
                    if(content2type.containsKey(word.content)){
                        word.type = content2type.get(word.content).toString();
                    }else{
                        word.type="IDENFR";
                    }
                    word.line_num = thisLine.line_num;
                    words.add(word);
                }
                //数字串
                else if(isDigit(txt.charAt(j))){
                    Numbers word = new Numbers();
                    StringBuilder word_buffer = new StringBuilder();
                    while(j < txt.length()&&isDigit(txt.charAt(j))){
                        word_buffer.append(txt.charAt(j));
                        j++;
                    }j--;
                    word.content = word_buffer.toString();
                    word.type = "INTCON";
                    word.line_num = thisLine.line_num;
                    words.add(word);
                }
                //格式字符串
                else if(txt.charAt(j) == '\"'){
                    //a类错误处理：格式字符串含有非法字符
                    int d_num = 0;//记录%d出现次数
                    boolean error_a = false;

                    FormatString word = new FormatString();
                    StringBuilder word_buffer = new StringBuilder("\"");
                    j++;
                    while(j < txt.length()&&txt.charAt(j) != '\"' ){
                        word_buffer.append(txt.charAt(j));

                        if(txt.charAt(j)==32||txt.charAt(j)==33||txt.charAt(j)>=40&&txt.charAt(j)<=126){
                            if(txt.charAt(j)==92&&(txt.charAt(j+1)!='n')){
                                error_a = true;
                            }
                        }else if(txt.charAt(j)=='%'&&txt.charAt(j + 1)=='d'){
                            d_num ++;
                        }else{
                            error_a = true;
                        }
                        j++;
                    }
                    word_buffer.append('\"');
                    word.content = word_buffer.toString();
                    word.line_num = thisLine.line_num;
                    word.type = "STRCON";
                    word.d_num = d_num;
                    if(error_a){
                        iw_errors.addError(word.line_num,"a");
                    }
                    //a类错误处理完毕
                    words.add(word);
                }
                //其他符号
                else {
                    Symbols word = new Symbols();
                    StringBuilder word_buffer = new StringBuilder();
                    //识别双字符
                    if (txt.charAt(j) == '!' && txt.charAt(j + 1) == '=') {
                        word.content = "!=";
                        word.type = "NEQ";
                    } else if (txt.charAt(j) == '>' && txt.charAt(j + 1) == '=') {
                        word.content = ">=";
                        word.type = "GEQ";
                    } else if (txt.charAt(j) == '<' && txt.charAt(j + 1) == '=') {
                        word.content = "<=";
                        word.type = "LEQ";
                    } else if (txt.charAt(j) == '=' && txt.charAt(j + 1) == '=') {
                        word.content = "==";
                        word.type = "EQL";
                    } else if (txt.charAt(j) == '&' && txt.charAt(j + 1) == '&') {
                        word.content = "&&";
                        word.type = "AND";
                    } else if (txt.charAt(j) == '|' && txt.charAt(j + 1) == '|') {
                        word.content = "||";
                        word.type = "OR";
                    }
                    //识别单字符
                    else {
                        word.content = String.valueOf(txt.charAt(j));
                        word.type = content2type.get(word.content).toString();
                        j--;//单字符不需要+j 这里预先减掉
                    }
                    word.line_num = thisLine.line_num;
                    words.add(word);
                    j++;
                }
            }
        }
    }

    public ArrayList<Line> getLines(){
        return this.lines;
    }
    public ArrayList<Word> getWords(){
        return this.words;
    }
    public Map getType(){
        return this.content2type;
    }

}

abstract class Word{    // this is the way of dalao's
    String content;
    int line_num;
    String type;
    int d_num;
}

class Identifier extends Word{
//    String type = "IDENFR";

}

class Numbers extends Word{
//    String type = "INTCON";
}

class FormatString extends Word{
//    String type = "STRCON";
}

class Symbols extends Word{

}