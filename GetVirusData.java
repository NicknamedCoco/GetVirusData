package world.keyi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author 万一
 * @DATE 2022年03月18日19:34
 */
public class GetVirusData {
    public static void main(String[] args) throws InterruptedException {
        //总的数据集，三个元素分别存放总的确诊，死亡，治愈人数
        ArrayList<ArrayList<Object[]>> arrayLists = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ArrayList<Object[]> arrayList = new ArrayList<>();
            arrayLists.add(arrayList);
        }

        RestTemplate restTemplate = new RestTemplate();
        ArrayList<Thread> threads = new ArrayList<>();
        //currentMonth为现在所处月份，days是获取哪些天数数据，例如获取的是2020年2月的1,10,20号数据
        //注意days数组最后一个时间不会获取，例如本案例中不会获取2022年3月20号数据，因为现在还没到这个时间呢
        int[] years = new int[]{2020,2021,2022};
        int[] days = new int[]{1,10,20};
        int currentMonth=3;
        boolean stopFlag=false;
        String[] countries = new String[]{"China","US","United Kingdom","France","Japan","Russia","Germany","Canada","Australia"};
        for (int year:years){
            for (int i = 1; i < 13; i++) {
                //请求的地址最早2020年2月才有数据
                if(year==years[0]&&i==1){
                    continue;
                }
                int j=i;
                for (int day:days){
                    //判断何时停止
                    if (year==years[years.length-1]&&i==currentMonth&&day==days[days.length-1]){
                        stopFlag=true;
                        break;
                    }
                    threads.add(new Thread(()->{
                        String date = j + "-" + day + "-" + year;
                        ArrayList<LinkedHashMap> arr = restTemplate.getForObject("https://covid19.mathdro.id/api/daily/"+date, ArrayList.class);
                        getOneDayData(date,arr,countries,arrayLists);
                    }));
                }
                if (stopFlag){
                    break;
                }
            }
        }

        //主线程启动线程，并等待数据
        threads.forEach(Thread::start);
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        //对数据进行排序
        for (ArrayList<Object[]> arrayList:arrayLists){
            arrayList.sort(new Comparator<Object[]>() {
                @Override
                public int compare(Object[] o1, Object[] o2) {
                    return (int)o1[2] - (int) o2[2];
                }
            });
            arrayList.add(0,new Object[]{"Num","Country","Year"});
        }

        //遍历数据并打印
        ObjectMapper mapper = new ObjectMapper();
        for (int i = 0; i < 3; i++) {
            try {
                String result = mapper.writeValueAsString(arrayLists.get(i));
                System.out.println(i+":"+result);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getOneDayData(String date,ArrayList<LinkedHashMap> arr,String[] countries,ArrayList<ArrayList<Object[]>> arrayLists) {

        //存放所有国家一天中感染，死亡，痊愈人数
        HashMap<String, ArrayList<Object[]>> hashMap = new HashMap<>();

        //对时间进行转换
        Integer dateFormat = dateFormat(date);

        //将数据进行过滤，封装到hashmap中
        for (LinkedHashMap map:arr){
            //解决国家名称变化问题
            String countryRegion = String.valueOf(map.get("countryRegion"));
            if (countryRegion.equals("Mainland China")){
                countryRegion="China";
            }else if(countryRegion.equals("UK")){
                countryRegion="United Kingdom";
            }else if (countryRegion.equals("Russian Federation")){
                countryRegion="Russia";
            }
            for (String country : countries){
                if (countryRegion.equals(country)){
                    String confirmed = (String) map.get("confirmed");
                    String deaths = (String) map.get("deaths");
                    String recovered = (String) map.get("recovered");
                    int confirmedNum = confirmed.equals("") ? 0 : Integer.parseInt(confirmed);
                    int deathsNum = deaths.equals("") ? 0 : Integer.parseInt(deaths);
                    int recoveredNum = recovered.equals("") ? 0 : Integer.parseInt(recovered);
                    if (!hashMap.containsKey(country)){
                        ArrayList<Object[]> objects = new ArrayList<>();
                        Object[] confirmedList = new Object[3];  //存一天中某个国家感染人数
                        Object[] deathList = new Object[3];  //存一天中某个国家死亡人数
                        Object[] recoveredList = new Object[3];  //存一天中某个国家治愈人数
                        confirmedList[0]= confirmedNum;
                        confirmedList[1]=country;
                        confirmedList[2]=dateFormat;
                        deathList[0]= deathsNum;
                        deathList[1]=country;
                        deathList[2]=dateFormat;
                        recoveredList[0]= recoveredNum;
                        recoveredList[1]=country;
                        recoveredList[2]=dateFormat;
                        objects.add(confirmedList);
                        objects.add(deathList);
                        objects.add(recoveredList);
                        hashMap.put(country,objects);
                    }else {
                        ArrayList<Object[]> oldObjects = hashMap.get(country);
                        oldObjects.get(0)[0]=(Integer)oldObjects.get(0)[0]+ confirmedNum;
                        oldObjects.get(1)[0]=(Integer)oldObjects.get(1)[0]+ deathsNum;
                        oldObjects.get(2)[0]=(Integer)oldObjects.get(2)[0]+ recoveredNum;
                        hashMap.put(country,oldObjects);
                    }
                    break;
                }
            }
        }

        //将数据封装到arrayLists中，这个对象中有三个ArrayList<Object[]>，对应着确诊，死亡，痊愈所有数据
        Collection<ArrayList<Object[]>> values = hashMap.values();
        for (int i = 0; i < 3; i++) {
            for (ArrayList<Object[]> value:values){
                arrayLists.get(i).add(value.get(i));
//                synchronized (GetVirusData.class){
//                }
            }
        }
    }

    private static Integer dateFormat(String date){
        //原来格式3-10-2020
        String[] strings = date.split("-");
        String month=strings[0];
        String day=strings[1];

        if (Integer.valueOf(strings[0])<10){
            month="0"+strings[0];
        }
        if (Integer.valueOf(strings[1])<10){
            day="0"+strings[1];
        }
        return Integer.valueOf(strings[2]+month+day);
    }
}
