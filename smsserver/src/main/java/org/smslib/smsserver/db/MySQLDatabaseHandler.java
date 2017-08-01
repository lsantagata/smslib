
package org.smslib.smsserver.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smslib.callback.events.DeliveryReportCallbackEvent;
import org.smslib.callback.events.InboundCallCallbackEvent;
import org.smslib.callback.events.InboundMessageCallbackEvent;
import org.smslib.callback.events.MessageSentCallbackEvent;
import org.smslib.helper.Common;
import org.smslib.message.AbstractMessage.Encoding;
import org.smslib.message.MsIsdn;
import org.smslib.message.OutboundBinaryMessage;
import org.smslib.message.OutboundMessage;
import org.smslib.message.OutboundMessage.SentStatus;
import org.smslib.smsserver.SMSServer;
import org.smslib.smsserver.db.data.GatewayDefinition;
import org.smslib.smsserver.db.data.GroupDefinition;
import org.smslib.smsserver.db.data.GroupRecipientDefinition;
import org.smslib.smsserver.db.data.NumberRouteDefinition;

public class MySQLDatabaseHandler extends JDBCDatabaseHandler implements IDatabaseHandler
{
	static Logger logger = LoggerFactory.getLogger(SMSServer.class);

	public MySQLDatabaseHandler(String dbUrl, String dbDriver, String dbUsername, String dbPassword)
	{
		super(dbUrl, dbDriver, dbUsername, dbPassword);
	}

	@Override
	public Collection<GatewayDefinition> getGatewayDefinitions(String profile) throws Exception
	{
		Collection<GatewayDefinition> gatewayList = new LinkedList<GatewayDefinition>();
		Connection db = getDbConnection();
		Statement s = db.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet rs = s.executeQuery("select class, gateway_id, ifnull(p0, ''), ifnull(p1, ''), ifnull(p2, ''), ifnull(p3, ''), ifnull(p4, ''), ifnull(p5, ''), ifnull(sender_address, ''), priority, max_message_parts, delivery_reports from smslib_gateways where (profile = '*' or profile = '" + profile + "') and enabled = 1");
		while (rs.next())
		{
			int fIndex = 0;
			GatewayDefinition g = new GatewayDefinition(rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getString(++fIndex).trim(), rs.getInt(++fIndex), rs.getInt(++fIndex), (rs.getInt(++fIndex) == 1));
			gatewayList.add(g);
		}
		rs.close();
		s.close();
		db.close();
		return gatewayList;
	}

	@Override
	public Collection<NumberRouteDefinition> getNumberRouteDefinitions(String profile) throws Exception
	{
		Collection<NumberRouteDefinition> routeList = new LinkedList<NumberRouteDefinition>();
		Connection db = getDbConnection();
		Statement s = db.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet rs = s.executeQuery("select address_regex, gateway_id from smslib_number_routes where (profile = '*' or profile = '" + profile + "') and enabled = 1");
		while (rs.next())
		{
			NumberRouteDefinition r = new NumberRouteDefinition(rs.getString(1).trim(), rs.getString(2).trim());
			routeList.add(r);
		}
		rs.close();
		s.close();
		db.close();
		return routeList;
	}

	@Override
	public Collection<GroupDefinition> getGroupDefinitions(String profile) throws Exception
	{
		Collection<GroupDefinition> groups = new LinkedList<GroupDefinition>();
		Connection db = getDbConnection();
		Statement s1 = db.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		ResultSet rs1 = s1.executeQuery("select id, group_name, group_description from smslib_groups where (profile = '*' or profile = '" + profile + "') and enabled = 1");
		while (rs1.next())
		{
			int groupId = rs1.getInt(1);
			String groupName = rs1.getString(2).trim();
			String groupDescription = rs1.getString(3).trim();
			List<GroupRecipientDefinition> recipients = new LinkedList<GroupRecipientDefinition>();
			PreparedStatement s2 = db.prepareStatement("select address from smslib_group_recipients where group_id = ? and enabled = 1");
			s2.setInt(1, groupId);
			ResultSet rs2 = s2.executeQuery();
			while (rs2.next())
				recipients.add(new GroupRecipientDefinition(rs2.getString(1).trim()));
			rs2.close();
			groups.add(new GroupDefinition(groupName, groupDescription, recipients));
		}
		rs1.close();
		s1.close();
		db.close();
		return groups;
	}

	@Override
	public void setMessageStatus(OutboundMessage message, OutboundMessage.SentStatus status) throws Exception
	{
		Connection db = null;
		PreparedStatement s = null;
		try
		{
			db = getDbConnection();
			s = db.prepareStatement("update smslib_out set sent_status = ? where message_id = ?");
			s.setString(1, status.toShortString());
			s.setString(2, message.getId());
			s.executeUpdate();
			db.commit();
		}
		catch (Exception e)
		{
			if (db != null) db.rollback();
			logger.error("Error!", e);
			throw e;
		}
		finally
		{
			if (s != null) s.close();
			if (db != null) db.close();
		}
	}

	@Override
	public void saveInboundCall(InboundCallCallbackEvent event) throws Exception
	{
		Connection db = null;
		PreparedStatement s = null;
		try
		{
			db = getDbConnection();
			s = db.prepareStatement("insert into smslib_calls (date, address, gateway_id) values (?, ?, ?)");
			s.setTimestamp(1, new Timestamp(event.getDate().getTime()));
			s.setString(2, event.getMsisdn().getAddress());
			s.setString(3, event.getGatewayId());
			s.executeUpdate();
			db.commit();
		}
		catch (Exception e)
		{
			if (db != null) db.rollback();
			logger.error("Error!", e);
			throw e;
		}
		finally
		{
			if (s != null) s.close();
			if (db != null) db.close();
		}
	}

	@Override
	public void saveDeliveryReport(DeliveryReportCallbackEvent event) throws Exception
	{
		Connection db = null;
		PreparedStatement s = null;
		try
		{
			db = getDbConnection();
			s = db.prepareStatement("update smslib_out set delivery_status = ?, delivery_date = ? where address = ? and operator_message_id = ? and gateway_id = ?");
			s.setString(1, event.getMessage().getDeliveryStatus().toShortString());
			s.setTimestamp(2, new Timestamp(event.getMessage().getOriginalReceivedDate().getTime()));
			s.setString(3, event.getMessage().getRecipientAddress().getAddress());
			s.setString(4, event.getMessage().getOriginalOperatorMessageId());
			s.setString(5, event.getMessage().getGatewayId());
			s.executeUpdate();
			db.commit();
		}
		catch (Exception e)
		{
			if (db != null) db.rollback();
			logger.error("Error!", e);
			throw e;
		}
		finally
		{
			if (s != null) s.close();
			if (db != null) db.close();
		}
	}

	@Override
	public void saveInboundMessage(InboundMessageCallbackEvent event) throws Exception
	{
		Connection db = null;
		PreparedStatement s = null;
		try
		{
			db = getDbConnection();
			s = db.prepareStatement("insert into smslib_in (address, encoding, text, message_date, receive_date, gateway_id) values (?, ?, ?, ?, ?, ?)");
			s.setString(1, event.getMessage().getOriginatorAddress().getAddress());
			s.setString(2, event.getMessage().getEncoding().toShortString());
			switch (event.getMessage().getEncoding())
			{
				case Enc7:
				case EncUcs2:
					s.setString(3, event.getMessage().getPayload().getText());
					break;
				case Enc8:
					s.setString(3, Common.bytesToString(event.getMessage().getPayload().getBytes()));
					break;
				case EncCustom:
					throw new UnsupportedOperationException();
			}
			s.setTimestamp(4, new Timestamp(event.getMessage().getSentDate().getTime()));
			s.setTimestamp(5, new Timestamp(event.getMessage().getCreationDate().getTime()));
			s.setString(6, event.getMessage().getGatewayId());
			s.executeUpdate();
			db.commit();
		}
		catch (Exception e)
		{
			if (db != null) db.rollback();
			logger.error("Error!", e);
			throw e;
		}
		finally
		{
			if (s != null) s.close();
			if (db != null) db.close();
		}
	}

	@Override
	public void markMessageSent(MessageSentCallbackEvent event) throws Exception
	{
		Connection db = null;
		PreparedStatement s = null;
		try
		{
			db = getDbConnection();
			if (event.getMessage().getSentStatus() == SentStatus.Sent)
			{
				s = db.prepareStatement("update smslib_out set sent_status = ?, sent_date = ?, gateway_id = ?, operator_message_id = ? where message_id = ?");
				s.setString(1, event.getMessage().getSentStatus().toShortString());
				s.setTimestamp(2, new Timestamp((event.getMessage().getSentStatus() == SentStatus.Sent ? event.getMessage().getSentDate().getTime() : 0)));
				s.setString(3, (event.getMessage().getSentStatus() == SentStatus.Sent ? event.getMessage().getGatewayId() : ""));
				s.setString(4, (event.getMessage().getSentStatus() == SentStatus.Sent ? event.getMessage().getOperatorMessageId() : ""));
				s.setString(5, event.getMessage().getId());
			}
			else
			{
				s = db.prepareStatement("update smslib_out set sent_status = ? where message_id = ?");
				s.setString(1, event.getMessage().getSentStatus().toShortString());
				s.setString(2, event.getMessage().getId());
			}
			s.executeUpdate();
			db.commit();
		}
		catch (Exception e)
		{
			if (db != null) db.rollback();
			logger.error("Error!", e);
			throw e;
		}
		finally
		{
			if (s != null) s.close();
			if (db != null) db.close();
		}
	}

	public Collection<OutboundMessage> getMessagesToSend() throws Exception
	{
		Collection<OutboundMessage> messages = new LinkedList<OutboundMessage>();
		Connection db = null;
		PreparedStatement s = null;
		ResultSet rs = null;
		try
		{
			db = getDbConnection();
			s = db.prepareStatement("select message_id, sender_address, address, text, encoding, priority, request_delivery_report, flash_sms from smslib_out join configuration_management_tools_campaign c on parent_id=c.id and c.is_active=1 and time(now()) between c.start and c.end where sent_status = ? order by priority desc limit 50");
			s.setString(1, OutboundMessage.SentStatus.Unsent.toShortString());
			rs = s.executeQuery();
			while (rs.next())
			{
				String messageId = rs.getString(1).trim();
				if (!Common.isNullOrEmpty(messageId))
				{
					OutboundMessage message;
					String senderId = rs.getString(2).trim();
					String recipient = rs.getString(3).trim();
					String text = rs.getString(4).trim();
					String encoding = rs.getString(5).trim();
					if (Encoding.getEncodingFromShortString(encoding) == Encoding.Enc7)
					{
						message = new OutboundMessage(new MsIsdn(recipient), text);
					}
					else if (Encoding.getEncodingFromShortString(encoding) == Encoding.Enc8)
					{
						message = new OutboundBinaryMessage(new MsIsdn(recipient), Common.stringToBytes(text));
					}
					else if (Encoding.getEncodingFromShortString(encoding) == Encoding.EncUcs2)
					{
						message = new OutboundMessage(new MsIsdn(recipient), text);
						message.setEncoding(Encoding.EncUcs2);
					}
					else
					{
						//TODO: ENC-CUSTOM
						message = new OutboundMessage(new MsIsdn(recipient), text);
					}
					message.setEncoding(Encoding.getEncodingFromShortString(encoding));
					message.setId(messageId);
					if (!Common.isNullOrEmpty(senderId)) message.setOriginatorAddress(new MsIsdn(senderId));
					message.setPriority(rs.getInt(6));
					message.setRequestDeliveryReport(rs.getInt(7) == 1);
					message.setFlashSms(rs.getInt(8) == 1);
					//if (!isGroupMessage(message)) Service.getInstance().queue(message);
					messages.add(message);
				}
			}
		}
		catch (Exception e)
		{
			logger.error("Error!", e);
			throw e;
		}
		finally
		{
			if (s != null) s.close();
			if (db != null) db.close();
		}
		return messages;
	}
}
