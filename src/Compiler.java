import java.io.*;
public class Compiler {
    public static void main(String[] args) throws Exception {

        Error globalErrorsManager = new Error();
        String txt = fileRead();
        System.setErr(new PrintStream("testfile.txt") );
        System.err.println();
//        System.out.println(txt.toString());
        Lexical_analysis la = new Lexical_analysis(txt.toString());
        globalErrorsManager.error_message.append(la.errors);
//        globalErrorsManager.output_errors();
//        PrintWords pw = new PrintWords(la.words);
        Syntactic_analysis sa = new Syntactic_analysis(la.words);
        sa.begin_analysis();
        globalErrorsManager.error_message.append(sa.sa_errors.map2String());
        globalErrorsManager.output_errors();

    }
    public static String fileRead() throws Exception{
        StringBuilder txt = new StringBuilder();
        File file = new File("testfile.txt");//定义一个file对象，用来初始化FileReader
        FileReader reader = new FileReader(file);//定义一个fileReader对象，用来初始化BufferedReader
        BufferedReader bReader = new BufferedReader(reader);//new一个BufferedReader对象，将文件内容读取到缓存
        String str = "";

        while ((str = bReader.readLine()) != null) {
            txt.append(str).append('\n');
        }
        bReader.close();
        return txt.toString();
    }
}

