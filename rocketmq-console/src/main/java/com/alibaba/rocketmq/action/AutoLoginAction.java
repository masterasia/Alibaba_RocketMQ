package com.alibaba.rocketmq.action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * console auto login.
 * must login in cockpit .
 */
@Controller
@RequestMapping("/authority")
public class AutoLoginAction {
    @Autowired
    private AuthenticationManager myAuthenticationManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @RequestMapping(value = "/login.do", method = {RequestMethod.GET, RequestMethod.POST})
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {

        boolean hasLoggedIn = false;
        try {
            UsernamePasswordAuthenticationToken token = getToken(request);
            if (null != token) {
                token.setDetails(new WebAuthenticationDetails(request));
                Authentication authenticatedUser = myAuthenticationManager.authenticate(token);
                SecurityContextHolder.getContext().setAuthentication(authenticatedUser);
                response.sendRedirect("../cluster/list.do");
                hasLoggedIn = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (!hasLoggedIn) {
                response.sendRedirect(
                        request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/");
            }
        }
    }

    private UsernamePasswordAuthenticationToken getToken(HttpServletRequest request) throws Exception {

        HttpSession session = request.getSession();
        String sessionId = session.getId();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 30);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT l.user_id, u.username, u.password " +
                "FROM login AS l " +
                "  JOIN cockpit_user AS u ON l.user_id = u.id " +
                " WHERE session_id = ? AND login_time < ?", sessionId, calendar.getTime());
        if (list.isEmpty()) {
            return null;
        }

        Map<String, Object> user = list.get(0);
        long userId = (Long)user.get("user_id");
        String userName = user.get("username").toString();
        String password = user.get("password").toString();

        List<Map<String, Object>> roles = jdbcTemplate.queryForList("SELECT r.name " +
                "FROM cockpit_role AS r " +
                "JOIN cockpit_user_role_xref AS xref ON xref.role_id = r.id " +
                "WHERE xref.user_id = ?", userId);

        List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>(roles.size());
        for (Map<String, Object> map : roles) {
            authorities.add(new SimpleGrantedAuthority(map.get("name").toString()));
        }

        return new UsernamePasswordAuthenticationToken(userName,
                password, authorities);
    }
}
