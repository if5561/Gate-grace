package com.grace.gateway.register.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网络工具类，主要用于获取本地IP地址
 * 支持根据IP前缀优先级选择合适的本地IP
 */
public class NetUtil {

    /**
     * 匹配IP地址与前缀数组，返回匹配到的索引
     * 用于判断IP是否符合指定的前缀规则，并确定其优先级
     * @param ip 待匹配的IP地址
     * @param prefix 前缀数组，按优先级排序
     * @return 匹配到的前缀索引，-1表示未匹配
     */
    private static int matchedIndex(String ip, String[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            String p = prefix[i];
            if ("*".equals(p)) { // "*"表示匹配非私有IP（公网IP优先）
                // 排除私有IP段，只匹配公网IP
                if (ip.startsWith("127.") ||  // 本地回环地址
                        ip.startsWith("10.") ||   // A类私有地址
                        ip.startsWith("172.") ||  // B类私有地址
                        ip.startsWith("192.")) {  // C类私有地址
                    continue;
                }
                return i; // 匹配到公网IP，返回当前索引
            } else {
                // 前缀匹配（如"10"匹配以10开头的IP）
                if (ip.startsWith(p)) {
                    return i;
                }
            }
        }
        return -1; // 未匹配任何前缀
    }

    /**
     * 根据IP优先级规则获取本地IP地址
     * @param ipPreference IP优先级字符串，格式如"*>10>172>192>127"
     * @return 符合优先级的本地IP，默认返回127.0.0.1
     */
    public static String getLocalIp(String ipPreference) {
        // 默认为"*>10>172>192>127"，即公网IP优先，其次是10段、172段、192段，最后是本地回环
        if (ipPreference == null) {
            ipPreference = "*>10>172>192>127";
        }
        // 将优先级字符串拆分为前缀数组（按>或空格分割）
        String[] prefix = ipPreference.split("[> ]+");

        try {
            // 正则表达式匹配IPv4地址（xxx.xxx.xxx.xxx格式）
            Pattern pattern = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+");
            // 获取所有网络接口，获取当前设备上所有的网络接口
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            String matchedIp = null; // 匹配到的IP
            int matchedIdx = -1;     // 匹配到的前缀索引（用于比较优先级）

            // 遍历所有网络接口
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                // 跳过回环接口和虚拟接口
                if (ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }

                // 获取当前网络接口的所有IP地址
                Enumeration<InetAddress> en = ni.getInetAddresses();
                while (en.hasMoreElements()) {
                    InetAddress addr = en.nextElement();
                    // 过滤掉回环地址、非本地站点地址和任意本地地址
                    if (addr.isLoopbackAddress() ||
                            !addr.isSiteLocalAddress() ||
                            addr.isAnyLocalAddress()) {
                        continue;
                    }

                    // 获取IP地址字符串
                    String ip = addr.getHostAddress();
                    // 验证是否为IPv4地址
                    Matcher matcher = pattern.matcher(ip);
                    if (matcher.matches()) {
                        // 检查当前IP是否匹配前缀数组，获取匹配索引
                        int idx = matchedIndex(ip, prefix);
                        if (idx == -1) {
                            continue; // 不匹配任何前缀，跳过
                        }

                        // 更新匹配结果：优先选择索引更小（优先级更高）的IP
                        if (matchedIdx == -1) {
                            matchedIdx = idx;
                            matchedIp = ip;
                        } else {
                            if (matchedIdx > idx) { // 新匹配的索引更小（优先级更高）
                                matchedIdx = idx;
                                matchedIp = ip;
                            }
                        }
                    }
                }
            }
            // 返回匹配到的IP，若无则返回127.0.0.1
            if (matchedIp != null)
                return matchedIp;
            return "127.0.0.1";
        } catch (Exception e) {
            // 发生异常时返回本地回环地址
            return "127.0.0.1";
        }
    }
    /**
     * 无参重载方法，使用默认IP优先级获取本地IP
     * 默认优先级：公网IP > 10段 > 172段 > 192段 > 127段
     * @return 本地IP地址
     */
    public static String getLocalIp() {
        return getLocalIp("*>10>172>192>127");
    }
}
