package kr.ac.gachon.sw.gbro.board;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Date;

import kr.ac.gachon.sw.gbro.R;
import kr.ac.gachon.sw.gbro.base.BaseActivity;
import kr.ac.gachon.sw.gbro.databinding.ActivityWriteBinding;
import kr.ac.gachon.sw.gbro.util.Auth;
import kr.ac.gachon.sw.gbro.util.CloudStorage;
import kr.ac.gachon.sw.gbro.util.Firestore;
import kr.ac.gachon.sw.gbro.util.LoadingDialog;
import kr.ac.gachon.sw.gbro.util.PathDialog;
import kr.ac.gachon.sw.gbro.util.Util;
import kr.ac.gachon.sw.gbro.util.model.Post;

public class WriteActivity extends BaseActivity<ActivityWriteBinding> implements AddImageAdapter.OnImageAddItemClickListener {
    private Post post;
    private boolean isModify = false;
    private ActionBar actionBar;
    private AddImageAdapter addImageAdapter;
    private RecyclerView addImageRecyclerView;
    private LoadingDialog loadingDialog;
    private PathDialog pathDialog; // 커스텀 다이얼로그
    private Button btn_path;
    private ArrayList<Integer> pathList;

    @Override
    protected ActivityWriteBinding getBinding() {
        return ActivityWriteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.write);
        }
        btn_path = binding.btnPath;
        loadingDialog = new LoadingDialog(this);  // Dialog 초기화
        pathDialog = new PathDialog(this);        // Dialog 초기화

        // 이미지 추가 RecyclerView 설정
        setAddImageRecyclerView();

        // 포스트 정보 가져오기
        Bundle bundle = getIntent().getExtras();
        if(bundle != null) {
            isModify = true;
            post = bundle.getParcelable("post");
            binding.rvPhoto.setEnabled(false);
            loadOriginalData();
        }
        else {
            post = new Post();
        }

        // 경로 선택 버튼을 누른다면
        btn_path.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 경로 선택 dialog 띄움.
                showPathDialog();
            }
        });

        // Post Type 선택 Item 이벤트
        binding.spinnerPosttype.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 습득물 선택시
                if(position == 1) {
                    // 경로 선택 비활성화
                    btn_path.setVisibility(View.GONE);
                    binding.tvPath.setVisibility(View.GONE);
                }
                // 분실물 선택시
                else {
                    // 경로 선택 활성화
                    btn_path.setVisibility(View.VISIBLE);
                    binding.tvPath.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        if(isModify) inflater.inflate(R.menu.writemenu_modify, menu);
        else inflater.inflate(R.menu.writemenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Google 정책에 따라 MenuItem에 Switch 사용하지 않고 if문 사용
        int itemId = item.getItemId();

        // 저장 버튼
        if (itemId == R.id.write_save) {
            savePost();
            return true;
        }
        else if(itemId == R.id.write_remove) {
            removePost();
            return true;
        }
        else if(itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onBackPressed() {
        closeDialog();
    }

    /**
     * 데이터가 날아갈 수 있다는 경고 Dialog를 출력한다 - 예를 누르면 Finish
     * @author Minjae Seon
     */
    private void closeDialog() {

        if(!binding.etContent.getText().toString().isEmpty() || !binding.etTitle.getText().toString().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.warning))
                    .setMessage(getString(R.string.post_cancel_dialog_msg))
                    .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    })
                    .setNegativeButton(getString(R.string.no), null)
                    .create().show();
        }
        else {
            finish();
        }
    }

    /**
     * 게시물을 삭제할 것인지 Dialog로 마지막으로 물어본다
     */
    public void removeDialog(){
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.warning))
                .setMessage(getString(R.string.post_remove_dialog_msg))
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeRealPost();
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.no), null)
                .create().show();
    }

    private void savePost(){
        // 제목과 내용이 비어있지 않으면
        if(!binding.etTitle.getText().toString().replaceAll("\\s", "").isEmpty() && !binding.etContent.getText().toString().replaceAll("\\s", "").isEmpty()){
            // 수정이 아니라면
            if(!isModify) {
                // 사진이 1장 이상 있다면
                if(addImageAdapter.getAllImageList().size() > 1) {
                    // 작성 Task
                    new WritePostTask().execute();
                }
                else {
                    Toast.makeText(this, R.string.post_nophoto, Toast.LENGTH_SHORT).show();
                }
            }
            // 수정이면 바로 업로드
            else {
                post.setTitle(binding.etTitle.getText().toString());
                post.setContent(binding.etContent.getText().toString());
                post.setSummaryBuildingType(binding.spinnerBuilding.getSelectedItemPosition());
                post.setType(binding.spinnerPosttype.getSelectedItemPosition() + 1);
                new WritePostTask().execute();
            }
        } else {
            Toast.makeText(getApplicationContext(),R.string.post_empty,Toast.LENGTH_SHORT).show();
        }
    }

    private void removePost(){
        removeDialog();
    }

    /**
     * Firestore에 있는 post 삭제
     */
    private void removeRealPost(){
        Log.d(getLocalClassName(), "postId : " + post.getPostId());
            Firestore.removePost(post.getPostId()).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(task.isSuccessful()){
                        Toast.makeText(getApplicationContext(), R.string.post_remove_success, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    else{
                        Toast.makeText(getApplicationContext(), R.string.post_remove_fail, Toast.LENGTH_SHORT).show();
                    }
                }
            });
    }


    /**
     * 원본 글 데이터를 불러온다
     */
    private void loadOriginalData() {
        binding.etTitle.setText(post.getTitle());
        binding.etContent.setText(post.getContent());
        binding.spinnerBuilding.setSelection(post.getSummaryBuildingType());
        binding.spinnerPosttype.setSelection(post.getType() - 1);
        showPathTextView(post.getSavePath());
    }

    /**
     * Image 추가를 위한 RecyclerView를 설정한다
     * @author Minjae Seon
     */
    private void setAddImageRecyclerView() {
        Util.debugLog(this, "setAddImageRecyclerView()");
        addImageAdapter = new AddImageAdapter(this);
        addImageRecyclerView = binding.rvPhoto;
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);

        addImageRecyclerView.setHasFixedSize(true);
        addImageRecyclerView.setLayoutManager(linearLayoutManager);
        addImageRecyclerView.setAdapter(addImageAdapter);
    }

    @Override
    public void onAddClick(View v) {
        if(!isModify) {
            ImagePicker.Companion.with(this)
                    .crop()
                    .galleryMimeTypes(new String[]{"image/png", "image/jpg", "image/jpeg"})
                    .compress(1024)
                    .maxResultSize(1080, 1080)
                    .start();
        }
        else {
            Toast.makeText(this, R.string.post_cantmodifyphoto, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRemoveClick(View v, int position) {
        if(!isModify) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.warning))
                    .setMessage(getString(R.string.post_deletephoto_msg))
                    .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            addImageAdapter.removeImage(position);
                        }
                    })
                    .setNegativeButton(getString(R.string.no), null)
                    .create().show();
        }
        else {
            Toast.makeText(this, R.string.post_cantmodifyphoto, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 경로 선택을 위한 path dialog를 보여준다
     * @author Taehyun Park
     */
    public void showPathDialog(){
        pathDialog.show();
        // 없음을 선택한다면 아래 스피너는 선택 못하게
        pathDialog.viewBinding.spinnerBuildingFirst.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                if(parent.getItemAtPosition(position).toString().equals("없음")){
                    pathDialog.viewBinding.spinnerBuildingSecond.setEnabled(false);
                    pathDialog.viewBinding.spinnerBuildingThird.setEnabled(false);
                    pathDialog.viewBinding.spinnerBuildingFourth.setEnabled(false);
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(false);
                }else{
                    pathDialog.viewBinding.spinnerBuildingSecond.setEnabled(true);
                    pathDialog.viewBinding.spinnerBuildingThird.setEnabled(true);
                    pathDialog.viewBinding.spinnerBuildingFourth.setEnabled(true);
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(true);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        pathDialog.viewBinding.spinnerBuildingSecond.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                if(parent.getItemAtPosition(position).toString().equals("없음")){
                    pathDialog.viewBinding.spinnerBuildingThird.setEnabled(false);
                    pathDialog.viewBinding.spinnerBuildingFourth.setEnabled(false);
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(false);
                }else{
                    pathDialog.viewBinding.spinnerBuildingThird.setEnabled(true);
                    pathDialog.viewBinding.spinnerBuildingFourth.setEnabled(true);
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(true);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        pathDialog.viewBinding.spinnerBuildingThird.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                if(parent.getItemAtPosition(position).toString().equals("없음")){
                    pathDialog.viewBinding.spinnerBuildingFourth.setEnabled(false);
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(false);
                }else{
                    pathDialog.viewBinding.spinnerBuildingFourth.setEnabled(true);
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(true);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        pathDialog.viewBinding.spinnerBuildingFourth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                if(parent.getItemAtPosition(position).toString().equals("없음")){
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(false);
                }else{
                    pathDialog.viewBinding.spinnerBuildingFifth.setEnabled(true);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) { }
        });
        // 완료 버튼을 누른다면
        pathDialog.viewBinding.btnPathFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pathList = new ArrayList<>();

                Spinner[] spinnerItem = { pathDialog.viewBinding.spinnerBuildingFirst,
                        pathDialog.viewBinding.spinnerBuildingSecond,
                        pathDialog.viewBinding.spinnerBuildingThird,
                        pathDialog.viewBinding.spinnerBuildingFourth,
                        pathDialog.viewBinding.spinnerBuildingFifth};

                for(Spinner item : spinnerItem) {
                    // 없음 선택한거 아니면
                    if(item.getSelectedItemPosition() != 25) {
                        Log.d(WriteActivity.this.getClass().getSimpleName(), "Added " + item.getSelectedItemPosition());
                        pathList.add(item.getSelectedItemPosition());
                    }
                    // 없음 선택시
                    else {
                        break;
                    }
                }

                post.setSavePath(pathList);
                showPathTextView(pathList);
                pathDialog.dismiss(); // 다이얼로그 닫기
            }
        });
    }

    private void showPathTextView(ArrayList<Integer> savePath){
        // 기존 5개 동선 데이터 그대로 보여주기
        String[] nameList = getResources().getStringArray(R.array.gachon_globalcampus_building2);
        StringBuilder pathStr = new StringBuilder();

        int i = 0;
        for(int path : savePath) {
            if(i != savePath.size() - 1) {
                pathStr.append(nameList[path] + " - ");
            }
            else {
                pathStr.append(nameList[path]);
            }
            i++;
        }
        binding.tvPath.setText(pathStr.toString());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == Activity.RESULT_OK) {
            Bitmap fileBitmap = BitmapFactory.decodeFile(ImagePicker.Companion.getFilePath(data));
            addImageAdapter.addImage(fileBitmap);
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
        }
    }

    private class WritePostTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected void onPreExecute() {
            loadingDialog.show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ArrayList<Bitmap> allImageList = addImageAdapter.getAllImageList();

            // 수정 아니라면
            if(!isModify) {
                // 사진 업로드
                ArrayList<String> photoUrl = new ArrayList<>();
                for (int i = 1; i < allImageList.size(); i++) {
                    int currentNum = i;
                    CloudStorage.uploadPostImg(allImageList.get(i))
                            .addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                                @Override
                                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                                    if (task.isSuccessful()) {
                                        task.getResult().getStorage().getDownloadUrl()
                                                .addOnCompleteListener(new OnCompleteListener<Uri>() {
                                                    @Override
                                                    public void onComplete(@NonNull Task<Uri> task) {
                                                        if (task.isSuccessful()) {
                                                            photoUrl.add(task.getResult().toString());

                                                            // 마지막 사진이면
                                                            if(currentNum == allImageList.size() - 1) {
                                                                // 포스트 작성
                                                                Post post = new Post(binding.spinnerPosttype.getSelectedItemPosition() + 1,
                                                                        binding.etTitle.getText().toString(),
                                                                        binding.etContent.getText().toString(),
                                                                        photoUrl,
                                                                        binding.spinnerBuilding.getSelectedItemPosition(),
                                                                        pathList,
                                                                        Auth.getCurrentUser().getUid(),
                                                                        new Timestamp(new Date()), false);

                                                                Firestore.writeNewPost(post)
                                                                        .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                                                                            @Override
                                                                            public void onComplete(@NonNull Task<DocumentReference> task) {
                                                                                if (task.isSuccessful()) {
                                                                                    loadingDialog.dismiss();
                                                                                    Toast.makeText(getApplicationContext(), R.string.post_success, Toast.LENGTH_SHORT).show();
                                                                                    finish();
                                                                                } else {
                                                                                    Toast.makeText(getApplicationContext(), R.string.error, Toast.LENGTH_SHORT).show();
                                                                                    loadingDialog.dismiss();
                                                                                }
                                                                            }
                                                                        });
                                                            }
                                                        }
                                                    }
                                                });
                                    } else {
                                        Util.debugLog(WriteActivity.this, "Photo #" + String.valueOf(currentNum) + " Upload Failed!");
                                    }
                                }
                            });
                }
            }
            // 수정이면 UpdatePost
            else {
                Firestore.updatePost(post.getPostId(), post).addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if(task.isSuccessful()) {
                            Toast.makeText(getApplicationContext(), R.string.post_success, Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        else {
                            Toast.makeText(getApplicationContext(), R.string.error, Toast.LENGTH_SHORT).show();
                            loadingDialog.dismiss();
                        }
                    }
                });
            }
            return null;
        }
    }
}
