package edu.ucsal.fiadopay.infrastructure.security.ratelimit;

import edu.ucsal.fiadopay.infrastructure.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final Map<String, RateLimitEntry> cache = new ConcurrentHashMap<>();

    private static class RateLimitEntry{
        long timestamp;
        AtomicInteger counter = new AtomicInteger(0);

        RateLimitEntry(long timestamp){
            this.timestamp = timestamp;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        if(!(handler instanceof HandlerMethod handlerMethod))
            return true;

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if(rateLimit == null)
            return true;

        String auth = request.getHeader("Authorization");
        if(auth == null || !auth.startsWith("Bearer FAKE-"))
            return true;

        String merchantId = auth.substring("Bearer FAKE-".length());
        long now = System.currentTimeMillis();
        long windowMs = rateLimit.windowSeconds() * 1000;

        cache.entrySet().removeIf(entry -> now - entry.getValue().timestamp > windowMs);

        RateLimitEntry entry = cache.computeIfAbsent(merchantId, k -> new RateLimitEntry(now));

        if(now - entry.timestamp > windowMs){
            entry.timestamp = now;
            entry.counter.set(0);
        }

        int currentCount = entry.counter.incrementAndGet();
        if(currentCount > rateLimit.maxRequest()){
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    String.format("Limite de requisições excedido: %d/%d requisições em %ds",
                            currentCount, rateLimit.maxRequest(), rateLimit.windowSeconds())
            );
        }

        return true;
    }
}
