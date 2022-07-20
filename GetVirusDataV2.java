package world.keyi.resourcemonitor;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * @author 万一
 * @DATE 2022年07月20日14:20
 */
@SpringBootTest
public class GetVirusDataV2 {

    @Test
    public void test() throws InterruptedException {
        //总的数据集，三个元素分别存放总的确诊，死亡，治愈人数
        ArrayList<ArrayList<Object[]>> arrayLists = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ArrayList<Object[]> arrayList = new ArrayList<>();
            arrayLists.add(arrayList);
        }
        RestTemplate restTemplate = new RestTemplate();
        ArrayList<Thread> threads = new ArrayList<>();
        //从2020年2月1日开始，每10天为一个时间点，一直到昨天，将这些时间点转成2-1-2020的字符串形式
        ArrayList<String> dateStrList = getDateStrList();
        String[] countries = new String[]{"China","US","United Kingdom","France","Japan","Russia","Germany","Canada","Australia"};
        dateStrList.forEach((dateStr)->{
            threads.add(new Thread(()->{
                ArrayList<LinkedHashMap> arr = restTemplate.getForObject("https://covid19.mathdro.id/api/daily/"+dateStr, ArrayList.class);
                if (Objects.nonNull(arr)){
                    getOneDayData(dateStr,arr,countries,arrayLists);
                }
            }));
        });

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
        arrayLists.forEach((arrayList->{
            arrayList.sort(new Comparator<Object[]>() {
                @Override
                public int compare(Object[] o1, Object[] o2) {
                    return (int)o1[2] - (int) o2[2];
                }
            });
            arrayList.add(0,new Object[]{"Num","Country","Year"});

        }));

        //将数据封装成json格式
        HashMap<String, ArrayList<Object[]>> result = new HashMap<>();
        result.put("confirm",arrayLists.get(0));
        result.put("death",arrayLists.get(1));
        result.put("recover",arrayLists.get(2));
        ObjectMapper mapper = new ObjectMapper();
        try {
            System.out.println(mapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

    }

    private ArrayList<String> getDateStrList() {
        //从2020年2月开始到昨天为止，获取每10天的时间格式，拼成 M-d-yyyy的形式
        ArrayList<String> allDateStr = new ArrayList<>();
        DateTime began = DateUtil.parse("2020-2-1", "yyyy-MM-dd");
        DateTime end = DateUtil.offsetDay(new Date(), -1);

        for (;began.isBefore(end);began=DateUtil.offsetDay(began,10)){
            String dateStr = DateUtil.format(began, "M-d-yyyy");
            allDateStr.add(dateStr);
        }
        return allDateStr;
    }

    private void getOneDayData(String date,ArrayList<LinkedHashMap> arr,String[] countries,ArrayList<ArrayList<Object[]>> arrayLists) {

        //存放所有国家一天中感染，死亡，痊愈人数
        HashMap<String, ArrayList<Object[]>> hashMap = new HashMap<>();

        //对时间进行转换
        int dateFormat = dateFormat(date);

        //将数据进行过滤，封装到hashmap中
        for (LinkedHashMap map:arr){
            //解决国家名称变化问题
            String countryRegion = String.valueOf(map.get("countryRegion"));
            switch (countryRegion) {
                case "Mainland China":
                    countryRegion = "China";
                    break;
                case "UK":
                    countryRegion = "United Kingdom";
                    break;
                case "Russian Federation":
                    countryRegion = "Russia";
                    break;
            }
            for (String country : countries){
                if (countryRegion.equals(country)){
                    String confirmed = (String) map.get("confirmed");
                    String deaths = (String) map.get("deaths");
                    String recovered = (String) map.get("recovered");
                    int confirmedNum = "0".equals(confirmed)||"".equals(confirmed)|| "0.0".equals(confirmed) ? 0 : Integer.parseInt(confirmed);
                    int deathsNum = "0".equals(deaths)|| "".equals(deaths)|| "0.0".equals(deaths) ? 0 : Integer.parseInt(deaths);
                    int recoveredNum = "0".equals(recovered)|| "".equals(recovered)|| "0.0".equals(recovered) ? 0 : Integer.parseInt(recovered);
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
                        oldObjects.get(0)[0]=(int)oldObjects.get(0)[0]+ confirmedNum;
                        oldObjects.get(1)[0]=(int)oldObjects.get(1)[0]+ deathsNum;
                        oldObjects.get(2)[0]=(int)oldObjects.get(2)[0]+ recoveredNum;
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
            }
        }
    }

    private Integer dateFormat(String date){
        DateTime dateTime = DateUtil.parse(date, "M-d-yyyy");
        String dateStr = DateUtil.format(dateTime, "yyyyMMdd");
        return Integer.parseInt(dateStr);
    }
}
