package org.genepattern.server.comment.dao;

import com.amazonaws.services.importexport.model.Job;
import org.apache.log4j.Logger;
import org.genepattern.server.DbException;
import org.genepattern.server.comment.JobComment;
import org.genepattern.server.database.HibernateUtil;
import org.hibernate.Query;

import java.util.List;

/**
 * Created by nazaire on 9/30/14.
 */
public class JobCommentDao
{
    private static final Logger log = Logger.getLogger(JobCommentDao.class);

    public void insertJobComment(final JobComment jobComment) {
        final boolean isInTransaction= HibernateUtil.isInTransaction();
        try {
            HibernateUtil.beginTransaction();
            HibernateUtil.getSession().saveOrUpdate(jobComment);
            if (!isInTransaction) {
                HibernateUtil.commitTransaction();
            }
        }
        catch (Throwable t) {
            log.error("Error adding comment for gpJobNo="+jobComment.getGpJobNo(), t);
            HibernateUtil.rollbackTransaction();
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }
    }

    public List<JobComment> selectJobComments(final Integer gpJobNo) throws DbException {
        final boolean isInTransaction=HibernateUtil.isInTransaction();
        try {
            HibernateUtil.beginTransaction();

            String hql = "from "+JobComment.class.getName()+" jc where jc.gpJobNo = :gpJobNo ";

            Query query = HibernateUtil.getSession().createQuery( hql );
            query.setInteger("gpJobNo", gpJobNo);
            List<JobComment> rval = query.list();
            return rval;
        }
        catch (Throwable t) {
            log.error("Error getting comments for gpJobNo="+gpJobNo,t);
            throw new DbException("Error getting comments for gpJobNo="+gpJobNo,t);
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }
    }

    public boolean updateJobComment(int id, int gpJobNo, String comment)
    {
        boolean updated = false;

        final boolean isInTransaction= HibernateUtil.isInTransaction();
        try {
            HibernateUtil.beginTransaction();
            JobComment jobComment = (JobComment)HibernateUtil.getSession().get(JobComment.class, Integer.valueOf(id));
            if(jobComment == null)
            {
                //log error and do nothing
                log.error("Error retrieving comment for gpJobNo="+gpJobNo);
                return updated;
            }

            jobComment.setComment(comment);

            HibernateUtil.getSession().saveOrUpdate(jobComment);

            if (!isInTransaction) {
                HibernateUtil.commitTransaction();
            }
            updated = true;
        }
        catch (Throwable t) {
            log.error("Error updating comment for gpJobNo="+gpJobNo, t);
            HibernateUtil.rollbackTransaction();
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }

        return updated;
    }

    public boolean deleteJobComment(int id)
    {
        boolean deleted = false;
        final boolean isInTransaction= HibernateUtil.isInTransaction();
        try {
            HibernateUtil.beginTransaction();

            JobComment jobComment = (JobComment)HibernateUtil.getSession().get(JobComment.class, Integer.valueOf(id));
            if(jobComment == null)
            {
                //log error and do nothing
                log.error("Error retrieving comment with id="+id);
                return deleted;
            }

            HibernateUtil.getSession().delete(jobComment);

            if (!isInTransaction) {
                HibernateUtil.commitTransaction();
            }

            deleted = true;
        }
        catch (Throwable t) {
            log.error("Error deleting comment wih id="+id, t);
            HibernateUtil.rollbackTransaction();
        }
        finally {
            if (!isInTransaction) {
                HibernateUtil.closeCurrentSession();
            }
        }

        return deleted;
    }
}
