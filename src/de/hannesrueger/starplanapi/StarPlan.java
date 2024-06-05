package de.hannesrueger.starplanapi;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import processing.data.JSONArray;
import processing.data.JSONObject;

/**
 * @author Hannes Rüger
 */
public class StarPlan {

    public static void main(String[] args) throws MalformedURLException {
        StarPlan splan = new StarPlan();
        String username = "PLACEHOLDER";
        String password = "PLACEHOLDER";
        if (!splan.login(username, password)) {
            System.out.println("Login failed - wrong credentials?");
            return;
        }
        StarPlanMyView myView = splan.getMyViewParameters();
        System.out.println(myView);
        StarPlanSemester[] semesters = splan.getSemesters();
        StarPlanSemester mySemester = semesters[0];
        for (StarPlanSemester semester : semesters) {
            if (semester.id == myView.semesterId) {
                mySemester = semester;
                break;
            }
        }
        StarPlanStudyProgram[] studyPrograms = splan.getStudyPrograms(mySemester);
        StarPlanStudyProgram myStudyProgram = studyPrograms[0];
        for (StarPlanStudyProgram studyProgram : studyPrograms) {
            if (studyProgram.id == myView.studyProgramId) {
                myStudyProgram = studyProgram;
                break;
            }
        }
        StarPlanGroup[] groups = splan.getGroups(mySemester, myStudyProgram);
        StarPlanGroup myGroup = groups[0];
        for (StarPlanGroup group : groups) {
            if (group.shortname.equals(myView.groupShortName)) {
                myGroup = group;
                break;
            }
        }
        StarPlanLesson[] events = StarPlanHelpers.sortEvents(splan.getTimeTableIcal(mySemester, myGroup));
        System.out.println(events[0]);
    }

    private String sessionId;

    public boolean login(String username, String password) {
        String url = "https://splan.hdm-stuttgart.de/splan/json?m=login";
        try {
            URL obj = new URI(url).toURL();
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            String body = username + "&" + password;
            con.getOutputStream().write(body.getBytes());
            String result;
            BufferedInputStream bis = new BufferedInputStream(con.getInputStream());
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int result2 = bis.read();
            while (result2 != -1) {
                buf.write((byte) result2);
                result2 = bis.read();
            }
            result = buf.toString();
            JSONArray json = JSONArray.parse(result);
            if (!((JSONObject) (((JSONArray) json.get(0)).get(0))).getString("res").equals("ok")) {
                throw new RuntimeException("Login failed");
            }

            List<String> allCookies = con.getHeaderFields().get("Set-Cookie");
            for (String cookie : allCookies) {
                if (cookie.startsWith("JSESSIONID")) {
                    sessionId = cookie.split("=")[1].split(";")[0];
                }
            }
            return true;
        } catch (Exception e) {
            // 401: login failed
            if (e.getMessage().contains("401")) {
                return false;
            } else {
                e.printStackTrace();
                return false;
            }
        }
    }

    public StarPlanMyView getMyViewParameters() {
        // do get request, get cookies
        try {
            URL url = new URI("https://splan.hdm-stuttgart.de/splan/json?m=getpus").toURL();
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Cookie", "JSESSIONID=" + sessionId);
            // get cookies
            for (int i = 0; i < con.getHeaderFields().get("Set-Cookie").size(); i++) {
                String cookies = con.getHeaderFields().get("Set-Cookie").get(i);
                String key = cookies.split("=")[0];
                String value = cookies.split("=")[1].split(";")[0];

                if (key.equals("myview")) {
                    value = value
                            .replaceAll("%3D", "=")
                            .replaceAll("%26", "&");
                    String[] kvPairs = value.split("&");
                    StarPlanMyView myView = new StarPlanMyView();
                    for (String kvPair : kvPairs) {
                        String[] kv = kvPair.split("=");
                        String k = kv[0];
                        String v = kv[1];
                        switch (k) {
                            case "lan":
                                myView.lan = v;
                                break;
                            case "acc":
                                myView.acc = Boolean.parseBoolean(v);
                                break;
                            case "act":
                                myView.act = v;
                                break;
                            case "sel":
                                myView.sel = v;
                                break;
                            case "pu":
                                myView.semesterId = Integer.parseInt(v);
                                break;
                            case "og":
                                myView.studyProgramId = Integer.parseInt(v);
                                break;
                            case "pg":
                                myView.groupShortName = v;
                                break;
                            case "sd":
                                myView.sd = Boolean.parseBoolean(v);
                                break;
                            case "loc":
                                myView.loc = Integer.parseInt(v);
                                break;
                            case "sa":
                                myView.sa = Boolean.parseBoolean(v);
                                break;
                            case "cb":
                                myView.cb = v;
                                break;
                        }
                    }
                    return myView;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public StarPlanSemester[] getSemesters() {
        String url = "https://splan.hdm-stuttgart.de/splan/json?m=getpus";
        JSONArray semesterJSON = getArray(url);
        StarPlanSemester[] semesters = new StarPlanSemester[semesterJSON.size()];
        for (int i = 0; i < semesterJSON.size(); i++) {
            JSONObject semester = (JSONObject) semesterJSON.get(i);
            semesters[i] = new StarPlanSemester(semester.getBoolean("dateasdefault"), semester.getString("enddate"),
                    semester.getString("name"), semester.getInt("id"), semester.getString("startdate"),
                    semester.getString("shortname"), semester.getBoolean("visibleonweb"));
        }
        return semesters;
    }

    public StarPlanStudyProgram[] getStudyPrograms(StarPlanSemester semester) {
        String url = "https://splan.hdm-stuttgart.de/splan/json?m=getogs&pu=" + semester.id;
        JSONArray studyProgramsJSON = getArray(url);
        StarPlanStudyProgram[] studyPrograms = new StarPlanStudyProgram[studyProgramsJSON.size()];
        for (int i = 0; i < studyProgramsJSON.size(); i++) {
            JSONObject studyProgram = (JSONObject) studyProgramsJSON.get(i);
            studyPrograms[i] = new StarPlanStudyProgram(studyProgram.getInt("id"), studyProgram.getString("name"),
                    studyProgram.getString("shortname"));
        }
        return studyPrograms;
    }

    public StarPlanGroup[] getGroups(StarPlanSemester semester, StarPlanStudyProgram studyProgram) {
        String url = "https://splan.hdm-stuttgart.de/splan/json?m=getPgsExt&pu=" + semester.id + "&og="
                + studyProgram.id;
        JSONArray studyProgramsWithLecturesJSON = getArray(url);
        StarPlanGroup[] studyProgramsWithLectures = new StarPlanGroup[studyProgramsWithLecturesJSON
                .size()];
        for (int i = 0; i < studyProgramsWithLecturesJSON.size(); i++) {
            JSONObject studyProgramWithLectures = (JSONObject) studyProgramsWithLecturesJSON.get(i);
            JSONArray lecturesJSON = studyProgramWithLectures.getJSONArray("lectures");
            StarPlanLecture[] lectures = new StarPlanLecture[lecturesJSON.size()];
            for (int j = 0; j < lecturesJSON.size(); j++) {
                JSONObject lecture = (JSONObject) lecturesJSON.get(j);
                lectures[j] = new StarPlanLecture(lecture.getInt("id"), lecture.getString("name"),
                        lecture.getString("shortname"));
            }
            studyProgramsWithLectures[i] = new StarPlanGroup(studyProgramWithLectures.getInt("id"),
                    studyProgramWithLectures.getString("name"), studyProgramWithLectures.getString("shortname"),
                    lectures);
        }
        return studyProgramsWithLectures;
    }

    public StarPlanLesson[] getTimeTableIcal(StarPlanSemester semester, StarPlanGroup group) {
        String url = "https://splan.hdm-stuttgart.de/splan/ical?lan=de&puid=" + semester.id + "&type=pg&pgid="
                + group.id;
        String ical = getString(url);
        IcalParser icalParser = new IcalParser();
        return icalParser.parse(ical);
    }

    private JSONArray getArray(String url) {
        String result = getString(url);
        JSONArray jsonArrayArray = JSONArray.parse(result);
        JSONArray resultArray = (JSONArray) jsonArrayArray.get(0);
        return resultArray;
    }

    private String getString(String url) {
        try {
            URL obj = new URI(url).toURL();
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Cookie", "JSESSIONID=" + sessionId);
            String result;
            BufferedInputStream bis = new BufferedInputStream(con.getInputStream());
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int result2 = bis.read();
            while (result2 != -1) {
                buf.write((byte) result2);
                result2 = bis.read();
            }
            result = buf.toString();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * @deprecated
     */
    public void getTimeTableWeekHtml(StarPlanSemester semester, StarPlanStudyProgram studyProgram,
            StarPlanGroup group) {
        String url = "https://splan.hdm-stuttgart.de/splan/json?m=getTT&sel=pg&pu=" + semester.id + "&og="
                + studyProgram.id + "&pg=" + group.shortname
                + "&sd=true&dfc=2024-06-04&loc=1&sa=false&cb=o";
        String html = getString(url);
        System.out.println(html);
        String dateRegex = "<div class=\"ttweekdaycell\" style=\"width:(\\d+)px; left:\\d+px;\"><span class=\"ttdaytext\" data-i18n=\".*?\">.*?<\\/span><div class=\"ttdatetext\"><span data-date=\"(\\d{4})-(\\d{2})-(\\d{2})\">.*?<\\/span><\\/div><\\/div>";
        // get the widths of all days
        Pattern pattern = Pattern.compile(dateRegex);
        Matcher matcher = pattern.matcher(html);
        int[] dayWidths = new int[5];
        for (int i = 0; i < 5; i++) {
            matcher.find();
            dayWidths[i] = Integer.parseInt(matcher.group(1));
        }
        int[] cummulatedDayWidths = new int[5];
        cummulatedDayWidths[0] = 0;
        for (int i = 1; i < 5; i++) {
            cummulatedDayWidths[i] = cummulatedDayWidths[i - 1] + dayWidths[i - 1];
        }

        String lessonRegex = "<div style=\"position:absolute; top:\\d+px; left:(-?\\d+)px;  width:\\d+px; height:\\d+px;\" class=\"ttevent weeklyg\" .*?><div class=\"tooltip\">.*?<\\/div>(.*?)<br\\/>.*?>(.*?)<\\/a><br\\/>(.*?)<br\\/>(.*?)<br\\/>(.*?)<br\\/>.*?<\\/div><\\/div>";
        pattern = Pattern.compile(lessonRegex);
        matcher = pattern.matcher(html);

        while (matcher.find()) {
            int left = Integer.parseInt(matcher.group(1));
            String time = matcher.group(2);
            String lecture = matcher.group(3);
            String room = matcher.group(4);
            String lecturer = matcher.group(5);
            String type = matcher.group(6);
            int day = 0;
            for (int i = 0; i < 5; i++) {
                if (left < cummulatedDayWidths[i]) {
                    day = i;
                    break;
                }
            }
            System.out.println("Day: " + day + " Time: " + time + " Lecture: " + lecture + " Room: " + room
                    + " Lecturer: " + lecturer + " Type: " + type);
        }
    }
}

class StarPlanSemester {
    int id;
    boolean dateasdefault;
    String enddate;
    String name;
    String startdate;
    String shortname;
    boolean visibleonweb;

    public StarPlanSemester(boolean dateasdefault, String enddate, String name, int id, String startdate, String shortname,
            boolean visibleonweb) {
        this.dateasdefault = dateasdefault;
        this.enddate = enddate;
        this.name = name;
        this.id = id;
        this.startdate = startdate;
        this.shortname = shortname;
        this.visibleonweb = visibleonweb;
    }

    public String toString() {
        return "id: " + id + " dateasdefault: " + dateasdefault + " enddate: " + enddate + " name: " + name
                + " startdate: " + startdate + " shortname: " + shortname + " visibleonweb: " + visibleonweb;
    }
}

class StarPlanStudyProgram {
    int id;
    String name;
    String shortname;

    public StarPlanStudyProgram(int id, String name, String shortname) {
        this.id = id;
        this.name = name;
        this.shortname = shortname;
    }
}

class StarPlanGroup {
    int id;
    String name;
    String shortname;
    StarPlanLecture[] lectures;

    public StarPlanGroup(int id, String name, String shortname, StarPlanLecture[] lectures) {
        this.id = id;
        this.name = name;
        this.shortname = shortname;
        this.lectures = lectures;
    }
}

class StarPlanLecture {
    int id;
    String name;
    String shortname;

    public StarPlanLecture(int id, String name, String shortname) {
        this.id = id;
        this.name = name;
        this.shortname = shortname;
    }
}

class StarPlanLesson {
    Date start;
    Date end;
    String summary;
    String id;
    String location;
    String description;

    public String toString() {
        System.out.println("start: " + start);
        System.out.println("end: " + end);
        System.out.println("summary: " + summary);
        System.out.println("id: " + id);
        System.out.println("location: " + location);
        System.out.println("description: " + description);
        return "";
    }
}

class IcalParser {
    public StarPlanLesson[] parse(String ical) {
        String[] lines = ical.split("\n");

        List<StarPlanLesson> events = new ArrayList<StarPlanLesson>();
        StarPlanLesson event = null;

        // remove lines until first BEGIN:VEVENT
        int i = 0;
        while (!lines[i].startsWith("BEGIN:VEVENT")) {
            i++;
        }

        for (int j = i; j < lines.length; j++) {
            String line = lines[j];
            if (line.startsWith("BEGIN:VEVENT")) {
                event = new StarPlanLesson();
            } else if (line.startsWith("DTSTART")) {
                String[] parts = line.split(":");
                event.start = parseDate(parts[1]);
            } else if (line.startsWith("DTEND")) {
                String[] parts = line.split(":");
                event.end = parseDate(parts[1]);
            } else if (line.startsWith("SUMMARY")) {
                String[] parts = line.split(":");
                event.summary = parts[1];
            } else if (line.startsWith("UID")) {
                String[] parts = line.split(":");
                event.id = parts[1];
            } else if (line.startsWith("LOCATION")) {
                String[] parts = line.split(":");
                event.location = parts[1];
            } else if (line.startsWith("DESCRIPTION")) {
                String[] parts = line.split(":");
                event.description = parts[1];
            } else if (line.startsWith("END:VEVENT")) {
                events.add(event);
            }
        }

        return events.toArray(new StarPlanLesson[0]);
    }

    private Date parseDate(String dateString) {
        Date date = new Date();
        date.setYear(Integer.parseInt(dateString.substring(0, 4)));
        date.setMonth(Integer.parseInt(dateString.substring(4, 6)));
        date.setDate(Integer.parseInt(dateString.substring(6, 8)));
        date.setHours(Integer.parseInt(dateString.substring(9, 11)));
        date.setMinutes(Integer.parseInt(dateString.substring(11, 13)));
        date.setSeconds(0);
        return date;
    }
}

class StarPlanMyView {
    String lan;
    boolean acc;
    String act;
    String sel;
    int semesterId;
    int studyProgramId;
    String groupShortName;
    boolean sd;
    int loc;
    boolean sa;
    String cb;

    public String toString() {
        return "lan: " + lan + " acc: " + acc + " act: " + act + " sel: " + sel + " semesterId: " + semesterId + " studyProgramId: " + studyProgramId
                + " groupShortName: " + groupShortName + " sd: " + sd + " loc: " + loc + " sa: " + sa + " cb: " + cb;
    }
}

class StarPlanHelpers {
    static StarPlanLesson[] sortEvents(StarPlanLesson[] events) {
        for (int i = 0; i < events.length; i++) {
            for (int j = i + 1; j < events.length; j++) {
                if (events[i].start.after(events[j].start)) {
                    StarPlanLesson temp = events[i];
                    events[i] = events[j];
                    events[j] = temp;
                }
            }
        }
        return events;
    }

    static StarPlanLesson getFirstEvent(StarPlanLesson[] events) {
        StarPlanLesson[] sortedEvents = sortEvents(events);
        return sortedEvents[0];
    }

    static StarPlanLesson getLastEvent(StarPlanLesson[] events) {
        StarPlanLesson[] sortedEvents = sortEvents(events);
        return sortedEvents[sortedEvents.length - 1];
    }
}