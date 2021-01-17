package net.jumperz.app.MWalu.util;

import java.util.Arrays;

public class MUtil
{

public static int indexOfChar( final String target, final char c )
{
	final char[] array = target.toCharArray();
	for( int i = 0; i < array.length; ++i )
	{
		if( array[ i ] == c )
		{
			return i;
		}
	}
	return -1;
}

public static int[] getCharCount( final String s )
{
	final int[] charCountArray = new int[ 96 ];
	Arrays.fill( charCountArray, 0 );
	for( final char c : s.toCharArray() )
	{
		final int i = ( int )c;
		if( 33 <= i && i <= 95 )
		{
			charCountArray[ i ]++;
		}
	}
	return charCountArray;
}

public static int countIgnoreCase2( final String target, final String patternStr )
{
	return count( target, patternStr.toUpperCase() ) + count( target, patternStr.toLowerCase() );
}

public static int count( final String target, final String patternStr )
{
	int count = 0;
	int fromIndex = 0;
	while( true )
	{
		final int index = target.indexOf( patternStr, fromIndex );
		if( index == -1 )
		{
			break;
		}
		else
		{
			fromIndex = index + patternStr.length();
			++count;
		}
	}
	return count;
}

public static int getAlphaNumCount( final String s )
{
	int alphaNum = 0;
	for( final char c : s.toCharArray() )
	{
		if( 65 <= c && c <= 90 )
		{
			++alphaNum;
		}
		else if( 97 <= c && c <= 122 )
		{
			++alphaNum;
		}
		else if( 48 <= c && c <= 57 )
		{
			++alphaNum;
		}
	}
	return alphaNum;
}

public static int getMaxNonAlnumLength( final String s )
{
	final char[] array = s.toCharArray();
	int length = 0;
	int max = 0;
	for( int i = 0; i < s.length(); ++i )
	{
		char c = array[ i ];
		if( ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') )
		{
			if( length > max )
			{
				max = length;
			}
			length = 0;
		}
		else
		{
			++length;
		}
	}
	if( length > max )
	{
		max = length;
	}
	return max;
}

public static int getNonAlnumCount( final String s )
{
	final char[] array = s.toCharArray();
	int count = 0;
	for( int i = 0; i < array.length; ++i )
	{
		char c = array[ i ];
		if( ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || ('0' <= c && c <= '9') )
		{
		}
		else
		{
			++count;
		}
	}
	return count;
}

}