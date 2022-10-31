import java.util.ArrayList;
import java.util.List;

public class Symbol_Table {
    List<Symbol> symbols = new ArrayList<>();
    Symbol_Table parent = null;
    String funcName = "";
    //构造函数
    public Symbol_Table(Symbol_Table its_parent,String funcName){
        this.parent = its_parent;
        this.funcName = funcName;
    }
    public Symbol_Table(){

    }
    public void addNewSymbol(String name,String type,int dimension,int decl_line,int valid){
        this.symbols.add(new Symbol(name,type,dimension,decl_line,valid));
    }
    public Symbol lookupSymbol(String name,int curLine){
        Symbol_Table curSymbolTable = this;
        while(curSymbolTable != null){
            for(Symbol symbol : curSymbolTable.symbols){
                if(symbol.name.equals(name)){
                    return symbol;
                }
            }
            curSymbolTable = curSymbolTable.parent;
        }
        return null;
    }
    public Symbol lookupValidSymbol(String name,int curLine){
        Symbol_Table curSymbolTable = this;
        while(curSymbolTable != null){
            for(Symbol symbol : curSymbolTable.symbols){
                if(symbol.name.equals(name)&&symbol.valid == 1){
                    return symbol;
                }
            }
            curSymbolTable = curSymbolTable.parent;
        }
        return null;
    }
    public Symbol lookupLastSymbol(String name,int curLine){
        Symbol_Table curSymbolTable = this;
        Symbol curSymbol = null;
        while(curSymbolTable != null){
            for(Symbol symbol : curSymbolTable.symbols){
                if(symbol.name.equals(name)){
                    curSymbol = symbol;
                }
            }
            curSymbolTable = curSymbolTable.parent;
        }
        return curSymbol;
    }
    public boolean lookupCurSymbolTable(String name){
        for(Symbol symbol : this.symbols){
            if(symbol.name.equals(name)){
                return true;
            }
        }
        return false;
    }
    public int occurTimes(String name){
        int time = 0;
        for(Symbol symbol : this.symbols){
            if(symbol.name.equals(name)){
                time++;
            }
        }
        return time;
    }
    public void output_symbols(){
        for(Symbol symbol : this.symbols){
            System.out.println(symbol.name + " " + symbol.type );
        }
    }
}

class Symbol{
    int valid = 1;//为1表示该符号为有效符号
    String name;
    int dimension;
    int decl_line;//声明行号
    String type = "";//const or var(变量) or void or int(函数)
    List<Integer> use_lines = new ArrayList<>();//使用行号
    List<Param> params = new ArrayList<>();//对于函数来说它的形参个数和维度记录
    int value;//对于(非数组)变量和常量来说需要记录它的值
    public Symbol(String name,String type,int dimension,int decl_line){
        this.name = name;
        this.type = type;
        this.dimension = dimension;
        this.decl_line = decl_line;
    }
    public Symbol(String name,String type,int dimension,int decl_line,int valid){
        this.name = name;
        this.type = type;
        this.dimension = dimension;
        this.decl_line = decl_line;
        this.valid = valid;
    }
}

class Param{
    int dimension;//参数维度可能是0/1/2
    int second_length;//如果这个参数是二维的，那么它将拥有一个第二维的长度
    public Param(int dimension){
        this.dimension = dimension;
    }
    public Param(int dimension,int second_length){
        this.dimension = dimension;
        this.second_length = second_length;
    }
}
