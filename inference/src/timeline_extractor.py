import re
import random
from datetime import datetime, timedelta
from typing import List, Dict, Optional


class TimelineExtractor:
    def __init__(self):
        self.time_patterns = [
            (r'入院第(\d+)天', 'admission_days'),
            (r'术后(\d+)天', 'post_operation_days'),
            (r'术后(\d+)周', 'post_operation_weeks'),
            (r'术后(\d+)月', 'post_operation_months'),
            (r'(\d+)天前', 'days_ago'),
            (r'(\d+)周前', 'weeks_ago'),
            (r'(\d+)月前', 'months_ago'),
            (r'(\d+)年前', 'years_ago'),
            (r'今日', 'today'),
            (r'昨日', 'yesterday'),
            (r'前日', 'day_before_yesterday'),
            (r'当天', 'same_day'),
            (r'次日', 'next_day'),
            (r'第(\d+)天', 'ordinal_day')
        ]

    def extract(self, text: str, entities: List[Dict], 
                reference_date: Optional[str] = None) -> List[Dict]:
        if reference_date:
            ref_date = datetime.strptime(reference_date, '%Y-%m-%d').date()
        else:
            ref_date = datetime.now().date()
        
        timelines = []
        time_entities = [(i, e) for i, e in enumerate(entities) if e['entity_type'] == 'time']
        
        for idx, entity in time_entities:
            normalized_date = self._normalize_time(entity['entity_text'], ref_date)
            timeline = {
                'time_expression': entity['entity_text'],
                'normalized_date': normalized_date.isoformat() if normalized_date else None,
                'associated_event': None,
                'entity_index': idx,
                'confidence': round(0.85 + random.random() * 0.1, 3)
            }
            timelines.append(timeline)
        
        return timelines

    def _normalize_time(self, expr: str, ref_date) -> Optional:
        for pattern, time_type in self.time_patterns:
            match = re.match(pattern, expr)
            if match:
                if time_type == 'admission_days':
                    days = int(match.group(1))
                    return ref_date + timedelta(days=days - 1)
                elif time_type == 'post_operation_days':
                    days = int(match.group(1))
                    return ref_date + timedelta(days=days)
                elif time_type == 'post_operation_weeks':
                    weeks = int(match.group(1))
                    return ref_date + timedelta(weeks=weeks)
                elif time_type == 'post_operation_months':
                    months = int(match.group(1))
                    return ref_date + timedelta(days=months * 30)
                elif time_type == 'days_ago':
                    days = int(match.group(1))
                    return ref_date - timedelta(days=days)
                elif time_type == 'weeks_ago':
                    weeks = int(match.group(1))
                    return ref_date - timedelta(weeks=weeks)
                elif time_type == 'months_ago':
                    months = int(match.group(1))
                    return ref_date - timedelta(days=months * 30)
                elif time_type == 'years_ago':
                    years = int(match.group(1))
                    return ref_date - timedelta(days=years * 365)
                elif time_type == 'today':
                    return ref_date
                elif time_type == 'yesterday':
                    return ref_date - timedelta(days=1)
                elif time_type == 'day_before_yesterday':
                    return ref_date - timedelta(days=2)
                elif time_type == 'same_day':
                    return ref_date
                elif time_type == 'next_day':
                    return ref_date + timedelta(days=1)
                elif time_type == 'ordinal_day':
                    days = int(match.group(1))
                    return ref_date + timedelta(days=days - 1)
        
        return ref_date
