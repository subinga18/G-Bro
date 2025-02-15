package kr.ac.gachon.sw.gbro;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import kr.ac.gachon.sw.gbro.base.BaseActivity;
import kr.ac.gachon.sw.gbro.databinding.ActivitySplashBinding;

public class SplashActivity extends BaseActivity<ActivitySplashBinding> {
    Handler splashHandler;
    Runnable moveActivity;
    FirebaseAuth mAuth;

    @Override
    protected ActivitySplashBinding getBinding() {
        return ActivitySplashBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // 지정한 시간 이후에 Activity 이동을 위해 Handler 생성
        splashHandler = new Handler(Looper.getMainLooper());

        // Handler가 실행할 작업 설정
        moveActivity = () -> {
            // 유저가 로그인이 됐고 인증도 받았다면
            if(checkUser()) {
                // MainActivity로
                Intent mainActivityIntent = new Intent(this, MainActivity.class);
                startActivity(mainActivityIntent);
            }
            // 그게 아니라면
            else {
                // LoginActivity로
                Intent loginActivityIntent = new Intent(this, LoginActivity.class);
                startActivity(loginActivityIntent);
            }

            // 현재 액티비티 종료
            finish();
        };

        // 일정 시간 후 Runnable 안에 있는 명령을 실행
        splashHandler.postDelayed(moveActivity, 2000);
    }

    @Override
    public void onBackPressed() {
        if(moveActivity != null) splashHandler.removeCallbacks(moveActivity);
        super.onBackPressed();
    }

    private boolean checkUser() {
        FirebaseUser user = mAuth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }
}
