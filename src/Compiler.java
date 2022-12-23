import java.io.*;
public class Compiler {
    public static void main(String[] args) throws Exception {

        Error globalErrorsManager = new Error();
        String txt = fileRead();

        Lexical_analysis la = new Lexical_analysis(txt.toString());
//        globalErrorsManager.line_errors.putAll(la.la_errors.line_errors);

//        PrintWords pw = new PrintWords(la.words);
        Syntactic_analysis sa = new Syntactic_analysis(la.words);
        sa.begin_analysis();

//        globalErrorsManager.line_errors.putAll(sa.sa_errors.line_errors);
//        globalErrorsManager.output_errors();

        MidCodeManager midCodeManager = new MidCodeManager();
        midCodeManager.generateMidCode(sa);
//            输出中间代码
        System.out.println(midCodeManager.midCodeOutput);

        ObjectCodeManager objectCodeManager = new ObjectCodeManager(midCodeManager.midCodes,sa.name2symboltable);
//        System.out.println(objectCodeManager.output_data_and_text());//输出目标代码
        objectCodeManager.output_to_file();
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

