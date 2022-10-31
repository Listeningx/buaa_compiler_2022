import java.io.*;
import java.util.ArrayList;

public class PrintWords {
    ArrayList<Word> words = new ArrayList<>();
    public PrintWords(ArrayList<Word> in_words) throws IOException {
//        System.out.println(in_words);
        StringBuilder ans = new StringBuilder();
        this.words = in_words;
        for(int i = 0; i < words.size(); i++){
            String str = words.get(i).type + " " + words.get(i).content + "\n";
//            System.out.print(str);
            ans.append(str);
        }
        System.out.println(ans);
        File file = new File("output.txt");
        file.createNewFile();
        FileWriter writer = new FileWriter(file);
        writer.write(ans.toString());
        writer.close();
    }
}
