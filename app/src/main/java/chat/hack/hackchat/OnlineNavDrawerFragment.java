package chat.hack.hackchat;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 */
public class OnlineNavDrawerFragment extends Fragment {

    ArrayList<String> onlineList;
    ListView lvOnlineList;
    TextView tvHeading;
    OnlineNavDrawerAdapter adapter;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;

    public OnlineNavDrawerFragment() {
        // Required empty public constructor
    }

    GetDataInterface mGetDataInterface;
    PassDataInterface mPassDataInterface;

    public interface GetDataInterface {
        ArrayList<String> getDataList();
    }

    public interface PassDataInterface {
        void passData(String clicked);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mGetDataInterface = (GetDataInterface) activity;
        mPassDataInterface = (PassDataInterface) activity;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_online_nav_drawer, container, false);

        lvOnlineList = (ListView) view.findViewById(R.id.lvOnlineList);
        tvHeading = (TextView) view.findViewById(R.id.tvOnlineHeading);
        onlineList = new ArrayList<>();

        lvOnlineList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final String clicked = parent.getAdapter().getItem(position).toString();

                mPassDataInterface.passData("@" + clicked + " ");
                mDrawerLayout.closeDrawers();
            }
        });

        return view;
    }

    public void setUp(DrawerLayout drawerLayout, Toolbar toolbar) {
        mDrawerLayout = drawerLayout;
        mDrawerToggle = new ActionBarDrawerToggle(getActivity(), drawerLayout,
                toolbar, R.string.drawer_open, R.string.drawer_close) {

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);

                getActivity().invalidateOptionsMenu();

                if (mGetDataInterface != null) {
                    onlineList = mGetDataInterface.getDataList();

                    // Need to improve the below three lines; problem is, onlineList points to different
                    // list each time, so the ListView is never updated as the original onlineLIst is blank
                    adapter = new OnlineNavDrawerAdapter(onlineList, getActivity());
                    lvOnlineList.setAdapter(adapter);
                    adapter.notifyDataSetChanged();

                    tvHeading.setText("Online Users: " + onlineList.size());
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                getActivity().invalidateOptionsMenu();

            }
        };
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });
    }


}
