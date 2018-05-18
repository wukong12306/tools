package com;



import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ApplicationTests {

	/*@Autowired
	private UserDao userDao;*/
	
	@Test
	public void contextLoads() {

		//countBySelective
		/*User user = new User();
		user.setName("张1");
		long l = userDao.countBySelective(user);
		System.out.println(l);*/

		//selectByPrimaryKey
		/*User user1 = userDao.selectByPrimaryKey("4");
		System.out.println(user1.toString());*/

		//deleteByPrimaryKey
		/*int i = userDao.deleteByPrimaryKey("2");
		System.out.println(i);*/

		//updateByPrimaryKeySelective
		/*User user = new User();
		user.setId("3");
		user.setName("张3");
		int i = userDao.updateByPrimaryKeySelective(user);
		System.out.println(i);*/

		//insert
		/*User user = new User();
		user.setId("9");
		user.setName("张9");
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		String format = sdf.format(date);
		user.setCreateTime(format);
		user.setDate(format);
		user.setTime(format);
		int insert = userDao.insert(user);
		System.out.println(insert);*/

		//selectBySelective
		/*User user = new User();
		user.setDate("2018-05-16");
		List<User> users = userDao.selectBySelective(user, 0, 10, "id", "desc");
		//List<User> users = userDao.selectBySelective(user, 0, 10, null, null);
		System.out.println(Arrays.toString(users.toArray()));*/
	}

}
